package com.tanhab.holdtheseat.payment.kafka;

import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.SeatsRejected;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.payment.AbstractIntegrationTest;
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

/**
 * The project plan's "payment is never attempted" for the reject path, asserted rather than
 * assumed from the topology. A {@code SeatsRejected} shares the {@code seats} topic with
 * {@code SeatsHeld}, so it reaches this service — and must leave the payments table untouched.
 */
class RejectionCostsNothingTest extends AbstractIntegrationTest {

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
    void aRejectionOnTheSeatsTopicChargesNothing() {
        UUID rejectedBooking = UUID.randomUUID();
        publishToPartition(SeatsRejected.seatsUnavailable(
                rejectedBooking, SHOW, List.of(SEAT), List.of(SEAT)), 0);

        // A real charge behind it on the same partition. When its payment lands, the listener
        // has demonstrably consumed past the rejection — so the rejection was seen, not pending.
        UUID paidBooking = UUID.randomUUID();
        publishToPartition(SeatsHeld.of(paidBooking, SHOW, List.of(SEAT), "alice", 5000L,
                Instant.now().plusSeconds(600)), 0);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(paymentRepository.findByBookingId(paidBooking)).isPresent());

        assertThat(paymentRepository.findByBookingId(rejectedBooking)).isEmpty();
    }

    private void publishToPartition(DomainEvent event, int partition) {
        kafkaTemplate.send(Topics.SEATS, partition, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

}
