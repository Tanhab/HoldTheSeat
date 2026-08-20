package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.Topics;
import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Playground stage 5, as an assertion. Rewinding the group's bookmark redelivers every event
 * on the topic — the whole saga, in order — and the second pass must change nothing.
 *
 * <p>This is what the {@code processed_events} table is for. Without it a replay would
 * re-hold seats, re-emit {@code SeatsHeld}, and re-run settlement.
 */
class TopicReplayTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * {@code processed_events} is deliberately left alone. It is the ledger that makes a
     * replay a no-op, so clearing it would make earlier tests' events genuinely reprocess and
     * this test would be measuring the wrong thing.
     */
    @BeforeEach
    void resetState() {
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
        releaseSeats();
    }

    @AfterEach
    void restoreSharedState() {
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });
        releaseSeats();
    }

    private void releaseSeats() {
        jdbcClient.sql("UPDATE seats SET status = 'AVAILABLE' WHERE show_id = :showId")
                .param("showId", SHOW)
                .update();
    }

    @Test
    void replayingTheWholeTopicChangesNothing() throws Exception {
        UUID bookingId = UUID.randomUUID();
        List<UUID> seatIds = List.of(A1, A2);

        publish(BookingRequested.of(bookingId, SHOW, seatIds, "cust-replay"));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRowsFor(bookingId)).isEqualTo(1));

        publish(BookingConfirmed.of(bookingId, SHOW, seatIds));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(A1)).isEqualTo(SeatStatus.SOLD));

        Snapshot before = snapshot(bookingId);
        assertThat(before.outboxRows()).isEqualTo(1);
        assertThat(before.soldSeats()).contains(A1.toString(), A2.toString());
        assertThat(before.holdKeys()).isEmpty();

        rewindToBeginning();

        // Every event on the topic is delivered a second time. Hold it long enough that a
        // second pass would have finished and shown up.
        await().during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(25))
                .untilAsserted(() -> assertThat(snapshot(bookingId)).isEqualTo(before));
    }

    /**
     * Stops the listeners, moves the group's committed offsets back to zero on every
     * partition, and starts them again. Offsets cannot be altered while the group has live
     * members, which is why the containers stop first.
     */
    private void rewindToBeginning() throws ExecutionException, InterruptedException {
        List<MessageListenerContainer> containers = List.copyOf(listenerRegistry.getListenerContainers());
        containers.forEach(MessageListenerContainer::stop);

        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());

        try (Admin admin = Admin.create(config)) {
            // Asked, not assumed: bookings is declared by booking-service, which is not in
            // this context, so here the broker auto-created it with its own default.
            int partitions = admin.describeTopics(List.of(Topics.BOOKINGS))
                    .allTopicNames().get()
                    .get(Topics.BOOKINGS)
                    .partitions()
                    .size();

            Map<TopicPartition, OffsetAndMetadata> rewound = new HashMap<>();
            IntStream.range(0, partitions).forEach(partition ->
                    rewound.put(new TopicPartition(Topics.BOOKINGS, partition), new OffsetAndMetadata(0L)));

            admin.alterConsumerGroupOffsets(BookingEventListener.CONSUMER_GROUP, rewound).all().get();
        }

        containers.forEach(MessageListenerContainer::start);
    }

    private record Snapshot(int outboxRows, Set<String> soldSeats, Set<String> holdKeys,
                            SeatStatus a1, SeatStatus a2) {
    }

    private Snapshot snapshot(UUID bookingId) {
        return new Snapshot(outboxRowsFor(bookingId),
                redis.opsForSet().members(HoldKeys.sold(SHOW)),
                heldKeysUnderTest(),
                statusOf(A1), statusOf(A2));
    }

    /**
     * Scoped to this test's own seats. A global {@code keys("hold:*")} would pick up holds
     * left by other classes sharing this JVM's broker and Redis, which have nothing to do with
     * whether replaying this booking changed anything.
     */
    private Set<String> heldKeysUnderTest() {
        return Stream.of(HoldKeys.hold(SHOW, A1), HoldKeys.hold(SHOW, A2))
                .filter(redis::hasKey)
                .collect(Collectors.toSet());
    }

    private void publish(DomainEvent event) {
        kafkaTemplate.send(Topics.BOOKINGS, event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private SeatStatus statusOf(UUID seatId) {
        return seatRepository.findByIds(SHOW, List.of(seatId)).stream()
                .map(Seat::status)
                .collect(Collectors.toList())
                .getFirst();
    }

    private int outboxRowsFor(UUID bookingId) {
        return jdbcClient.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id")
                .param("id", bookingId)
                .query(Integer.class)
                .single();
    }

}
