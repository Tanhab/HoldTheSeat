package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim this whole service exists to support: under a flood of simultaneous requests for
 * one seat, exactly one wins, and a request that loses leaves nothing behind.
 *
 * <p>Runs against real Redis and real Postgres. A mocked store would only prove the mock is
 * atomic.
 */
class NoDoubleBookConcurrencyTest extends AbstractIntegrationTest {

    private static final int ATTEMPTS = 50;

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<UUID> ROW_A = List.of(
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005"));

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void exactlyOneBookingWinsWhenFiftyRaceForTheSameSeat() throws Exception {
        UUID contestedSeat = ROW_A.getFirst();
        Map<UUID, HoldOutcome> results = raceFor(booking -> List.of(contestedSeat));

        List<UUID> winners = results.entrySet().stream()
                .filter(e -> e.getValue().granted())
                .map(Map.Entry::getKey)
                .toList();

        assertThat(winners).hasSize(1);
        assertThat(results).hasSize(ATTEMPTS);
        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, contestedSeat)))
                .isEqualTo(winners.getFirst().toString());
    }

    @Test
    void everyLoserIsToldWhichSeatBeatIt() throws Exception {
        UUID contestedSeat = ROW_A.getFirst();
        Map<UUID, HoldOutcome> results = raceFor(booking -> List.of(contestedSeat));

        assertThat(results.values().stream().filter(outcome -> !outcome.granted()))
                .hasSize(ATTEMPTS - 1)
                .allSatisfy(outcome -> assertThat(outcome.conflictingSeatIds()).containsExactly(contestedSeat));
    }

    @Test
    void aLosingRequestLeavesNoHoldsAndNoReverseIndex() throws Exception {
        UUID contestedSeat = ROW_A.getFirst();
        Map<UUID, HoldOutcome> results = raceFor(booking -> List.of(contestedSeat));

        results.forEach((booking, outcome) -> {
            if (!outcome.granted()) {
                assertThat(redis.hasKey(HoldKeys.bookingHolds(booking)))
                        .as("loser %s left a reverse index behind", booking)
                        .isFalse();
            }
        });
    }

    /**
     * Overlapping multi-seat requests, each contending with two neighbours. The property
     * under test is all-or-nothing: a booking ends up holding every seat it asked for or
     * none of them, never a partial map that would strand a seat until its TTL lapsed.
     */
    @Test
    void overlappingMultiSeatRequestsAreNeverPartiallyHeld() throws Exception {
        int seats = ROW_A.size();
        Map<Integer, List<UUID>> requestBySlot = new ConcurrentHashMap<>();
        Map<UUID, Integer> slotByBooking = new ConcurrentHashMap<>();

        Map<UUID, HoldOutcome> results = race(seats, (booking, index) -> {
            List<UUID> pair = List.of(ROW_A.get(index), ROW_A.get((index + 1) % seats));
            requestBySlot.put(index, pair);
            slotByBooking.put(booking, index);
            return pair;
        });

        assertThat(results.values().stream().filter(HoldOutcome::granted)).isNotEmpty();

        results.forEach((booking, outcome) -> {
            List<UUID> requested = requestBySlot.get(slotByBooking.get(booking));
            long owned = requested.stream()
                    .filter(seat -> booking.toString().equals(redis.opsForValue().get(HoldKeys.hold(SHOW, seat))))
                    .count();

            if (outcome.granted()) {
                assertThat(owned)
                        .as("granted booking %s should own all %d requested seats", booking, requested.size())
                        .isEqualTo(requested.size());
            } else {
                assertThat(owned)
                        .as("refused booking %s should own no seats, owns %d", booking, owned)
                        .isZero();
            }
        });
    }

    private Map<UUID, HoldOutcome> raceFor(SeatSelector selector) throws Exception {
        return race(ATTEMPTS, (booking, index) -> selector.seatsFor(booking));
    }

    /**
     * All threads park on a start gate and are released together, so the requests overlap
     * rather than queueing politely one after another.
     */
    private Map<UUID, HoldOutcome> race(int attempts, IndexedSeatSelector selector) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        Map<UUID, HoldOutcome> results = new ConcurrentHashMap<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            IntStream.range(0, attempts).forEach(index -> {
                UUID booking = UUID.randomUUID();
                pool.submit(() -> {
                    try {
                        List<UUID> seats = selector.seatsFor(booking, index);
                        startGate.await();
                        results.put(booking, seatHoldService.hold(SHOW, booking, seats));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        finished.countDown();
                    }
                });
            });

            startGate.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS)).as("all attempts finished").isTrue();
        }

        assertThat(failures).as("no attempt threw").isEmpty();
        assertThat(results).hasSize(attempts);
        return results;
    }

    @FunctionalInterface
    private interface SeatSelector {
        List<UUID> seatsFor(UUID bookingId);
    }

    @FunctionalInterface
    private interface IndexedSeatSelector {
        List<UUID> seatsFor(UUID bookingId, int index);
    }

}
