package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.SeatsReleased;
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
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Expiry with a still-live hold — the misconfiguration backstop. TTL is pinned long so
 * only the release can free the seats; the normal path (TTL already gone) is S4.
 */
@TestPropertySource(properties = "holdtheseat.hold.ttl=10m")
class BookingExpiredListenerTest extends AbstractIntegrationTest {

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
    void anExpiryFreesStillHeldSeatsAndAnnouncesThem() {
        UUID bookingId = UUID.randomUUID();
        publish(BookingRequested.of(bookingId, SHOW, List.of(A1, B1), "cust-expire"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        publish(BookingExpired.of(bookingId, SHOW, List.of(A1, B1)));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(2));

        SeatsReleased released = readReleased(bookingId);
        assertThat(released.releasedCount()).isEqualTo(2);
        assertThat(released.seatIds()).containsExactlyInAnyOrder(A1, B1);

        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, B1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.bookingHolds(bookingId))).isFalse();
    }

    @Test
    void aRedeliveredExpiryReleasesOnce() {
        UUID bookingId = UUID.randomUUID();
        publish(BookingRequested.of(bookingId, SHOW, List.of(A2), "cust-dup-expire"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        BookingExpired expired = BookingExpired.of(bookingId, SHOW, List.of(A2));
        publish(expired);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(2));

        publish(expired);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(2));
        assertThat(processedEventRowsFor(expired.eventId())).isEqualTo(1);
    }

    private void publish(BookingRequested event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private void publish(BookingExpired event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private SeatsReleased readReleased(UUID bookingId) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", SeatsReleased.TYPE)
                .query(String.class)
                .single();

        return (SeatsReleased) objectMapper.readValue(payload, DomainEvent.class);
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
