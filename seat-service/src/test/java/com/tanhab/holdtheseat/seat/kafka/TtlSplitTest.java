package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.SeatsReleased;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The TTL split: Redis frees the hold with nothing running; BookingExpired then frees zero
 * and announces nothing. Absence of SeatsReleased is only meaningful after proving the
 * listener saw the event ({@code processed_events}).
 */
@TestPropertySource(properties = "holdtheseat.hold.ttl=2s")
class TtlSplitTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final List<UUID> SEATS = List.of(A1, B1);
    private static final Duration WELL_PAST_THE_TTL = Duration.ofSeconds(10);

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

    @Test
    void expiryAfterTtlLapseAnnouncesNothingBecauseRedisAlreadyFreedTheSeats() {
        UUID bookingId = UUID.randomUUID();
        publish(BookingRequested.of(bookingId, SHOW, SEATS, "cust-ttl-split"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isTrue();
            assertThat(redis.hasKey(HoldKeys.hold(SHOW, B1))).isTrue();
            assertThat(redis.hasKey(HoldKeys.bookingHolds(bookingId))).isTrue();
        });

        Set<UUID> soldBefore = soldMembers();

        await().atMost(WELL_PAST_THE_TTL).until(() ->
                !redis.hasKey(HoldKeys.hold(SHOW, A1))
                        && !redis.hasKey(HoldKeys.hold(SHOW, B1))
                        && !redis.hasKey(HoldKeys.bookingHolds(bookingId)));

        BookingExpired expired = BookingExpired.of(bookingId, SHOW, SEATS);
        publish(expired);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedEventRowsFor(expired.eventId())).isEqualTo(1));

        assertThat(releasedRowsFor(bookingId)).isZero();
        assertThat(statusOf(A1)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(statusOf(B1)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(soldMembers()).isEqualTo(soldBefore);
    }

    private void publish(BookingRequested event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private void publish(BookingExpired event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private SeatStatus statusOf(UUID seatId) {
        return seatRepository.findByIds(SHOW, List.of(seatId)).stream()
                .map(Seat::status)
                .findFirst()
                .orElseThrow();
    }

    private Set<UUID> soldMembers() {
        return SEATS.stream()
                .filter(seat -> Boolean.TRUE.equals(
                        redis.opsForSet().isMember(HoldKeys.sold(SHOW), seat.toString())))
                .collect(Collectors.toSet());
    }

    private int releasedRowsFor(UUID bookingId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM outbox
                        WHERE aggregate_id = :id AND event_type = :type
                        """)
                .param("id", bookingId)
                .param("type", SeatsReleased.TYPE)
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
