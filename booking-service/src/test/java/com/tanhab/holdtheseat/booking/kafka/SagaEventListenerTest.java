package com.tanhab.holdtheseat.booking.kafka;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.CancellationReason;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.RejectionReason;
import com.tanhab.holdtheseat.events.SeatsRejected;
import com.tanhab.holdtheseat.events.Topics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SagaEventListenerTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmsTheBookingAtThePriceTheSeatServiceQuoted() {
        BookingResponse pending = createBooking();
        assertThat(pending.amountCents()).isNull();

        publish(PaymentAuthorized.of(pending.id(), 8500L, "mock-ref"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
                    assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
                    assertThat(booking.amountCents()).isEqualTo(8500L);
                }));

        BookingConfirmed confirmed = readConfirmed(pending.id());
        assertThat(confirmed.showId()).isEqualTo(pending.showId());
        // Carried on the event so the seat service can settle without reading Redis key names.
        assertThat(confirmed.seatIds()).containsExactlyElementsOf(pending.seatIds());
    }

    @Test
    void aRedeliveredAuthorizationConfirmsOnce() {
        BookingResponse pending = createBooking();
        PaymentAuthorized authorized = PaymentAuthorized.of(pending.id(), 5000L, "mock-ref");

        publish(authorized);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(confirmedRowsFor(pending.id())).isEqualTo(1));

        publish(authorized);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(confirmedRowsFor(pending.id())).isEqualTo(1));
    }

    /**
     * A second authorization with a different event id gets past dedup and is stopped by the
     * PENDING guard instead — the reason both defences exist.
     */
    @Test
    void aSecondAuthorizationForTheSameBookingChangesNothing() {
        BookingResponse pending = createBooking();

        publish(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(confirmedRowsFor(pending.id())).isEqualTo(1));

        publish(PaymentAuthorized.of(pending.id(), 999_999L, "mock-ref-2"));

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(confirmedRowsFor(pending.id())).isEqualTo(1));
        assertThat(bookingRepository.findById(pending.id()))
                .hasValueSatisfying(booking -> assertThat(booking.amountCents()).isEqualTo(5000L));
    }

    @Test
    void aRejectionCancelsTheBookingAndAnnouncesTheReason() {
        BookingResponse pending = createBooking();

        publish(SeatsRejected.seatsUnavailable(pending.id(), pending.showId(), pending.seatIds(),
                pending.seatIds().subList(0, 1)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
                    assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
                    assertThat(booking.cancellationReason()).isEqualTo(CancellationReason.SEATS_REJECTED);
                }));

        BookingCancelled cancelled = readCancelled(pending.id());
        assertThat(cancelled.reason()).isEqualTo(CancellationReason.SEATS_REJECTED);
        assertThat(cancelled.seatIds()).containsExactlyElementsOf(pending.seatIds());

        // The same object the controller serialises, so the reason is visible at the API.
        assertThat(bookingService.findById(pending.id()).cancellationReason())
                .isEqualTo(CancellationReason.SEATS_REJECTED);
    }

    @Test
    void aRedeliveredRejectionCancelsOnce() {
        BookingResponse pending = createBooking();
        SeatsRejected rejected = SeatsRejected.seatsUnavailable(pending.id(), pending.showId(),
                pending.seatIds(), pending.seatIds().subList(0, 1));

        publish(rejected);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(cancelledRowsFor(pending.id())).isEqualTo(1));

        publish(rejected);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(cancelledRowsFor(pending.id())).isEqualTo(1));
    }

    /**
     * The PENDING guard, not dedup: this rejection has a fresh event id, so it clears the dedup
     * table and is stopped only by the guard finding the booking already CONFIRMED. A late
     * rejection must never unwind a paid booking.
     */
    @Test
    void aRejectionForAnAlreadyConfirmedBookingLeavesItConfirmed() {
        BookingResponse pending = createBooking();
        bookingService.confirm(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));
        assertThat(bookingRepository.findById(pending.id()))
                .hasValueSatisfying(b -> assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED));

        SeatsRejected late = SeatsRejected.seatsUnavailable(pending.id(), pending.showId(),
                pending.seatIds(), pending.seatIds().subList(0, 1));
        publish(late);

        // Wait for proof the listener actually handled it, then assert it changed nothing.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedRowsFor(late.eventId())).isEqualTo(1));

        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.cancellationReason()).isNull();
        });
        assertThat(cancelledRowsFor(pending.id())).isZero();
    }

    @Test
    void aPaymentFailureCancelsTheBookingAndAnnouncesIt() {
        BookingResponse pending = createBooking();

        publish(PaymentFailed.of(pending.id(), 8500L, "card_declined"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
                    assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
                    assertThat(booking.cancellationReason()).isEqualTo(CancellationReason.PAYMENT_FAILED);
                }));

        // The seats come from the booking row, not the PaymentFailed event, which carries none.
        BookingCancelled cancelled = readCancelled(pending.id());
        assertThat(cancelled.reason()).isEqualTo(CancellationReason.PAYMENT_FAILED);
        assertThat(cancelled.seatIds()).containsExactlyElementsOf(pending.seatIds());
    }

    @Test
    void anAuthorizationThenAFailureLeavesTheBookingConfirmed() {
        BookingResponse pending = createBooking();
        publish(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(confirmedRowsFor(pending.id())).isEqualTo(1));

        PaymentFailed late = PaymentFailed.of(pending.id(), 5000L, "card_declined");
        publish(late);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedRowsFor(late.eventId())).isEqualTo(1));
        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.cancellationReason()).isNull();
        });
        assertThat(cancelledRowsFor(pending.id())).isZero();
    }

    @Test
    void aFailureThenAnAuthorizationLeavesTheBookingCancelled() {
        BookingResponse pending = createBooking();
        publish(PaymentFailed.of(pending.id(), 5000L, "card_declined"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(cancelledRowsFor(pending.id())).isEqualTo(1));

        PaymentAuthorized late = PaymentAuthorized.of(pending.id(), 5000L, "mock-ref");
        publish(late);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedRowsFor(late.eventId())).isEqualTo(1));
        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.amountCents()).isNull();
        });
        assertThat(confirmedRowsFor(pending.id())).isZero();
    }

    private BookingResponse createBooking() {
        return bookingService.create(new CreateBookingRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), "cust-confirm"));
    }

    private void publish(PaymentAuthorized event) {
        kafkaTemplate.send(Topics.PAYMENTS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private void publish(SeatsRejected event) {
        kafkaTemplate.send(Topics.SEATS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private void publish(PaymentFailed event) {
        kafkaTemplate.send(Topics.PAYMENTS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private BookingCancelled readCancelled(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingCancelled.TYPE)
                .query(String.class)
                .single();

        return (BookingCancelled) objectMapper.readValue(payload, DomainEvent.class);
    }

    private int cancelledRowsFor(UUID bookingId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingCancelled.TYPE)
                .query(Integer.class)
                .single();
    }

    private int processedRowsFor(UUID eventId) {
        return jdbcClient.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

    private BookingConfirmed readConfirmed(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", BookingConfirmed.TYPE)
                .query(String.class)
                .single();

        return (BookingConfirmed) objectMapper.readValue(payload, DomainEvent.class);
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
