package com.tanhab.holdtheseat.booking.kafka;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
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

class PaymentEventListenerTest extends AbstractIntegrationTest {

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

    private BookingResponse createBooking() {
        return bookingService.create(new CreateBookingRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), "cust-confirm"));
    }

    private void publish(PaymentAuthorized event) {
        kafkaTemplate.send(Topics.PAYMENTS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
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
