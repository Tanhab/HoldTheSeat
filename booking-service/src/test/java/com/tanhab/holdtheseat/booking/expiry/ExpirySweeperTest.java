package com.tanhab.holdtheseat.booking.expiry;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls {@link BookingService#expireStale()} directly — the scheduler is not under test.
 * {@code created_at} is backdated with SQL so the threshold is asserted without sleeping.
 */
@TestPropertySource(properties = "holdtheseat.expiry.after=1m")
class ExpirySweeperTest extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aStalePendingBookingBecomesExpiredAndEmitsOnce() {
        BookingResponse pending = createBooking();
        backdateCreatedAt(pending.id(), "2 minutes");

        assertThat(bookingService.expireStale()).isEqualTo(1);

        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(booking.cancellationReason()).isNull();
        });
        assertThat(expiredRowsFor(pending.id())).isEqualTo(1);

        BookingExpired event = readExpired(pending.id());
        assertThat(event.showId()).isEqualTo(pending.showId());
        assertThat(event.seatIds()).containsExactlyElementsOf(pending.seatIds());
    }

    @Test
    void aFreshPendingBookingIsUntouched() {
        BookingResponse pending = createBooking();

        assertThat(bookingService.expireStale()).isZero();

        assertThat(bookingRepository.findById(pending.id()))
                .hasValueSatisfying(booking -> assertThat(booking.status()).isEqualTo(BookingStatus.PENDING));
        assertThat(expiredRowsFor(pending.id())).isZero();
    }

    @Test
    void anOldConfirmedBookingIsNeverExpired() {
        BookingResponse pending = createBooking();
        bookingService.confirm(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));
        backdateCreatedAt(pending.id(), "2 minutes");

        assertThat(bookingService.expireStale()).isZero();

        assertThat(bookingRepository.findById(pending.id()))
                .hasValueSatisfying(booking -> assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED));
        assertThat(expiredRowsFor(pending.id())).isZero();
    }

    @Test
    void aSecondSweepEmitsNoSecondBookingExpired() {
        BookingResponse pending = createBooking();
        backdateCreatedAt(pending.id(), "2 minutes");

        assertThat(bookingService.expireStale()).isEqualTo(1);
        assertThat(bookingService.expireStale()).isZero();

        assertThat(expiredRowsFor(pending.id())).isEqualTo(1);
    }

    @Test
    void paymentAuthorizedForAnExpiredBookingLeavesItExpired() {
        BookingResponse pending = createBooking();
        backdateCreatedAt(pending.id(), "2 minutes");
        assertThat(bookingService.expireStale()).isEqualTo(1);

        bookingService.confirm(PaymentAuthorized.of(pending.id(), 5000L, "late-ref"));

        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(booking.amountCents()).isNull();
        });
        assertThat(confirmedRowsFor(pending.id())).isZero();
    }

    private BookingResponse createBooking() {
        return bookingService.create(new CreateBookingRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), "cust-expiry"));
    }

    private void backdateCreatedAt(UUID bookingId, String interval) {
        jdbcClient.sql("""
                        UPDATE bookings
                        SET created_at = now() - cast(:interval as interval)
                        WHERE id = :id
                        """)
                .param("interval", interval)
                .param("id", bookingId)
                .update();
    }

    private BookingExpired readExpired(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingExpired.TYPE)
                .query(String.class)
                .single();

        return (BookingExpired) objectMapper.readValue(payload, DomainEvent.class);
    }

    private int expiredRowsFor(UUID bookingId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingExpired.TYPE)
                .query(Integer.class)
                .single();
    }

    private int confirmedRowsFor(UUID bookingId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingConfirmed.TYPE)
                .query(Integer.class)
                .single();
    }

}
