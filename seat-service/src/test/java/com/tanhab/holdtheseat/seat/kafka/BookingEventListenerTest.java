package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Drives the listener the way Kafka does: a real record on a real topic. Everything the
 * service does in response — the Redis hold, the dedup row, the outbox row — is asserted
 * from the outside.
 */
class BookingEventListenerTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID B1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearState() {
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbcClient.sql("DELETE FROM outbox").update();
        jdbcClient.sql("DELETE FROM processed_events").update();
    }

    @Test
    void holdsTheSeatsAndAnnouncesThePrice() {
        UUID bookingId = UUID.randomUUID();

        publish(BookingRequested.of(bookingId, SHOW, List.of(A1, B1), "cust-listener"));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, A1))).isEqualTo(bookingId.toString());
        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, B1))).isEqualTo(bookingId.toString());

        SeatsHeld held = readSeatsHeld(bookingId);
        assertThat(held.bookingId()).isEqualTo(bookingId);
        assertThat(held.showId()).isEqualTo(SHOW);
        assertThat(held.seatIds()).containsExactlyInAnyOrder(A1, B1);
        // 5000 for row A, 3500 for row B: the seat service prices the booking, not the client.
        assertThat(held.amountCents()).isEqualTo(8500L);
        assertThat(held.holdExpiresAt()).isAfter(held.occurredAt());
    }

    @Test
    void aRedeliveredEventHoldsNothingTwiceAndAnnouncesOnce() {
        UUID bookingId = UUID.randomUUID();
        BookingRequested requested = BookingRequested.of(bookingId, SHOW, List.of(A2), "cust-dup");

        publish(requested);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        // The same event id again, exactly as a rebalance or an outbox retry would deliver it.
        publish(requested);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));
        assertThat(processedEventRowsFor(requested.eventId())).isEqualTo(1);
    }

    @Test
    void aSeatAlreadyHeldByAnotherBookingIsRefusedWithoutAnEvent() {
        UUID winner = UUID.randomUUID();
        publish(BookingRequested.of(winner, SHOW, List.of(A1), "cust-winner"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(winner)).isEqualTo(1));

        UUID loser = UUID.randomUUID();
        BookingRequested refused = BookingRequested.of(loser, SHOW, List.of(A1, A2), "cust-loser");
        publish(refused);

        // Consumed and recorded, but nothing announced: the saga stops here until Phase 3.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedEventRowsFor(refused.eventId())).isEqualTo(1));
        assertThat(outboxRowsFor(loser)).isZero();

        // All-or-nothing still holds: the seat it could have had was not taken either.
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A2))).isFalse();
        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, A1))).isEqualTo(winner.toString());
    }

    private void publish(BookingRequested event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private SeatsHeld readSeatsHeld(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", SeatsHeld.TYPE)
                .query(String.class)
                .single();

        return (SeatsHeld) objectMapper.readValue(payload, com.tanhab.holdtheseat.events.DomainEvent.class);
    }

    private int outboxRowsFor(UUID bookingId) {
        return jdbcClient.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id")
                .param("id", bookingId)
                .query(Integer.class)
                .single();
    }

    private int processedEventRowsFor(UUID eventId) {
        return jdbcClient.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

}
