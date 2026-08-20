package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.CancellationReason;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Invariant #2, as an assertion: a batch of bookings held and then cancelled returns the seat
 * map to <em>exactly</em> where it started.
 *
 * <p>The hold TTL is pinned to ten minutes on purpose. With the ~2s TTL other tests use, Redis
 * would free the seats on its own and this test would pass whether or not compensation runs —
 * it would be measuring expiry, which Phase 1 already proved. A long TTL means the only thing
 * that can restore the map inside the test's lifetime is the release.
 */
@TestPropertySource(properties = "holdtheseat.hold.ttl=10m")
class CompensationRestoresTheSeatMapTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID A3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
    private static final UUID A4 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");
    private static final UUID B1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID B2 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final List<UUID> ALL = List.of(A1, A2, A3, A4, B1, B2);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SeatRepository seatRepository;

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

    @AfterEach
    void restoreSeats() {
        jdbcClient.sql("UPDATE seats SET status = 'AVAILABLE' WHERE show_id = :showId")
                .param("showId", SHOW)
                .update();
    }

    @Test
    void aBatchOfCancellationsReturnsTheSeatMapToItsOpeningState() {
        Map<UUID, List<UUID>> batch = new LinkedHashMap<>();
        batch.put(UUID.randomUUID(), List.of(A1, A2));
        batch.put(UUID.randomUUID(), List.of(A3, A4));
        batch.put(UUID.randomUUID(), List.of(B1, B2));

        SeatMap opening = snapshot();

        batch.forEach((bookingId, seats) ->
                publish(BookingRequested.of(bookingId, SHOW, seats, "cust-batch")));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(heldRows()).isEqualTo(batch.size()));

        // Without this the final assertion could pass on a batch that never held anything.
        assertThat(snapshot()).isNotEqualTo(opening);
        assertThat(snapshot().holdKeys()).isNotEmpty();

        batch.forEach((bookingId, seats) ->
                publish(BookingCancelled.of(bookingId, SHOW, seats, CancellationReason.PAYMENT_FAILED)));

        // The map restoration is the claim, and the TTL is pinned long so only the release can
        // satisfy it — a broken release leaves the holds standing here rather than expiring away.
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(snapshot()).isEqualTo(opening));

        // And the undo was announced once per booking, never for a booking that held nothing.
        assertThat(releasedRows()).isEqualTo(batch.size());
    }

    private record SeatMap(Map<UUID, SeatStatus> statuses, Set<String> holdKeys, Set<UUID> soldMembers) {
    }

    private SeatMap snapshot() {
        Map<UUID, SeatStatus> statuses = seatRepository.findByIds(SHOW, ALL).stream()
                .collect(Collectors.toMap(Seat::id, Seat::status));

        Set<String> holdKeys = ALL.stream()
                .map(seat -> HoldKeys.hold(SHOW, seat))
                .filter(redis::hasKey)
                .collect(Collectors.toSet());

        Set<UUID> soldMembers = ALL.stream()
                .filter(seat -> Boolean.TRUE.equals(
                        redis.opsForSet().isMember(HoldKeys.sold(SHOW), seat.toString())))
                .collect(Collectors.toSet());

        return new SeatMap(statuses, holdKeys, soldMembers);
    }

    private void publish(DomainEvent event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private int heldRows() {
        return outboxRowsOfType("SeatsHeld");
    }

    private int releasedRows() {
        return outboxRowsOfType("SeatsReleased");
    }

    private int outboxRowsOfType(String eventType) {
        return jdbcClient.sql("SELECT count(*) FROM outbox WHERE event_type = :type")
                .param("type", eventType)
                .query(Integer.class)
                .single();
    }

}
