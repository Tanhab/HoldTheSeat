package com.tanhab.holdtheseat.booking.outbox;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The Phase 2 claim in one test: a booking committed to Postgres reaches Kafka without the
 * request ever touching the broker.
 */
class OutboxPublishingTest extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aCreatedBookingIsAnnouncedOnTheBookingsTopic() {
        UUID showId = UUID.randomUUID();
        List<UUID> seatIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        BookingResponse created = bookingService.create(
                new CreateBookingRequest(showId, seatIds, "cust-outbox"));

        ConsumerRecord<String, String> record = awaitRecordFor(created.id());

        assertThat(record.key())
                .as("the message key is what keeps one booking's events on one partition")
                .isEqualTo(created.id().toString());

        DomainEvent event = objectMapper.readValue(record.value(), DomainEvent.class);
        assertThat(event).isInstanceOf(BookingRequested.class);

        BookingRequested requested = (BookingRequested) event;
        assertThat(requested.bookingId()).isEqualTo(created.id());
        assertThat(requested.showId()).isEqualTo(showId);
        assertThat(requested.seatIds()).containsExactlyElementsOf(seatIds);
        assertThat(requested.customerId()).isEqualTo("cust-outbox");
        assertThat(requested.eventId()).isNotNull();
        assertThat(requested.occurredAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void theOutboxRowIsWrittenInTheSameTransactionAndStampedOnceSent() {
        BookingResponse created = bookingService.create(
                new CreateBookingRequest(UUID.randomUUID(), List.of(UUID.randomUUID()), "cust-stamp"));

        // Committed with the booking, so it is queryable immediately.
        assertThat(outboxRowsFor(created.id())).isEqualTo(1);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(publishedRowsFor(created.id())).isEqualTo(1));
    }

    private ConsumerRecord<String, String> awaitRecordFor(UUID bookingId) {
        try (Consumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(Topics.BOOKINGS));

            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.records(Topics.BOOKINGS).forEach(seen::add);
                return seen.stream().anyMatch(r -> bookingId.toString().equals(r.key()));
            });

            return seen.stream()
                    .filter(r -> bookingId.toString().equals(r.key()))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static Consumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        // A fresh group per run, so a previous run's committed offsets cannot hide the record.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private int outboxRowsFor(UUID bookingId) {
        return countOutbox(bookingId, "");
    }

    private int publishedRowsFor(UUID bookingId) {
        return countOutbox(bookingId, " AND published_at IS NOT NULL");
    }

    private int countOutbox(UUID bookingId, String extraPredicate) {
        return jdbcClient.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id" + extraPredicate)
                .param("id", bookingId)
                .query(Integer.class)
                .single();
    }

}
