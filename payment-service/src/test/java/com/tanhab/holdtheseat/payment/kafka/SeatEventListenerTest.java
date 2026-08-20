package com.tanhab.holdtheseat.payment.kafka;

import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.payment.AbstractIntegrationTest;
import com.tanhab.holdtheseat.payment.domain.PaymentStatus;
import com.tanhab.holdtheseat.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SeatEventListenerTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearState() {
        jdbcClient.sql("DELETE FROM outbox").update();
        jdbcClient.sql("DELETE FROM processed_events").update();
        jdbcClient.sql("DELETE FROM payments").update();
    }

    @Test
    void chargesTheQuotedAmountAndAnnouncesIt() {
        UUID bookingId = UUID.randomUUID();

        publish(SeatsHeld.of(bookingId, SHOW, List.of(SEAT), "alice", 8500L, Instant.now().plusSeconds(600)));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(paymentRepository.findByBookingId(bookingId)).isPresent());

        assertThat(paymentRepository.findByBookingId(bookingId)).hasValueSatisfying(payment -> {
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            // The seat service quoted this, and payment charges what it was told.
            assertThat(payment.amountCents()).isEqualTo(8500L);
            assertThat(payment.gatewayRef()).startsWith("mock-");
        });

        PaymentAuthorized authorized = readAuthorized(bookingId);
        assertThat(authorized.bookingId()).isEqualTo(bookingId);
        assertThat(authorized.amountCents()).isEqualTo(8500L);
        assertThat(authorized.gatewayRef()).startsWith("mock-");
    }

    @Test
    void aRedeliveredEventChargesOnce() {
        UUID bookingId = UUID.randomUUID();
        SeatsHeld held = SeatsHeld.of(bookingId, SHOW, List.of(SEAT), "alice", 5000L, Instant.now().plusSeconds(600));

        publish(held);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        publish(held);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));
        assertThat(paymentRowsFor(bookingId)).isEqualTo(1);
    }

    @Test
    void aDeclinedCardWritesAFailedRowAndAnnouncesFailure() {
        UUID bookingId = UUID.randomUUID();

        publish(SeatsHeld.of(bookingId, SHOW, List.of(SEAT), "decline-me", 8500L,
                Instant.now().plusSeconds(600)));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(paymentRepository.findByBookingId(bookingId)).isPresent());

        assertThat(paymentRepository.findByBookingId(bookingId)).hasValueSatisfying(payment -> {
            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.gatewayRef()).isNull();
        });

        // Exactly one outbox row, and it is a failure — no authorization was announced.
        assertThat(outboxRowsFor(bookingId)).isEqualTo(1);
        PaymentFailed failed = readFailed(bookingId);
        assertThat(failed.amountCents()).isEqualTo(8500L);
        assertThat(failed.gatewayReason()).isEqualTo("card_declined");
    }

    @Test
    void aRedeliveredDeclineChargesOnce() {
        UUID bookingId = UUID.randomUUID();
        SeatsHeld held = SeatsHeld.of(bookingId, SHOW, List.of(SEAT), "decline-me", 5000L,
                Instant.now().plusSeconds(600));

        publish(held);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        publish(held);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));
        assertThat(paymentRowsFor(bookingId)).isEqualTo(1);
    }

    @Test
    void aDeclineDoesNotStallThePartition() {
        UUID declined = UUID.randomUUID();
        publishToPartition(SeatsHeld.of(declined, SHOW, List.of(SEAT), "decline-me", 5000L,
                Instant.now().plusSeconds(600)), 0);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(readFailed(declined).gatewayReason()).isEqualTo("card_declined"));

        // A well-formed approving charge behind the decline on the same partition still runs.
        UUID approved = UUID.randomUUID();
        publishToPartition(SeatsHeld.of(approved, SHOW, List.of(SEAT), "alice", 5000L,
                Instant.now().plusSeconds(600)), 0);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(paymentRepository.findByBookingId(approved))
                        .hasValueSatisfying(p -> assertThat(p.status()).isEqualTo(PaymentStatus.AUTHORIZED)));
    }

    private void publish(SeatsHeld event) {
        kafkaTemplate.send(Topics.SEATS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private void publishToPartition(SeatsHeld event, int partition) {
        kafkaTemplate.send(Topics.SEATS, partition, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private PaymentFailed readFailed(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", PaymentFailed.TYPE)
                .query(String.class)
                .single();

        return (PaymentFailed) objectMapper.readValue(payload, DomainEvent.class);
    }

    private PaymentAuthorized readAuthorized(UUID bookingId) {
        String payload = jdbcClient.sql("SELECT payload::text FROM outbox WHERE aggregate_id = :id")
                .param("id", bookingId)
                .query(String.class)
                .single();

        return (PaymentAuthorized) objectMapper.readValue(
                payload, com.tanhab.holdtheseat.events.DomainEvent.class);
    }

    private int outboxRowsFor(UUID bookingId) {
        return jdbcClient.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id")
                .param("id", bookingId)
                .query(Integer.class)
                .single();
    }

    private int paymentRowsFor(UUID bookingId) {
        return jdbcClient.sql("SELECT count(*) FROM payments WHERE booking_id = :id")
                .param("id", bookingId)
                .query(Integer.class)
                .single();
    }

}
