package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * An abandoned booking needs no cleanup code — the hold is a key with a TTL, so the seat
 * frees itself. A two-second window makes that assertable; production runs at ten minutes.
 */
@TestPropertySource(properties = "holdtheseat.hold.ttl=2s")
class HoldExpiryTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static final Duration WELL_PAST_THE_TTL = Duration.ofSeconds(10);

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
    void holdCarriesTheConfiguredTtl() {
        seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1));

        assertThat(redis.getExpire(HoldKeys.hold(SHOW, A1))).isBetween(1L, 2L);
    }

    @Test
    void abandonedHoldsDisappearWithNoCodeRunning() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        await().atMost(WELL_PAST_THE_TTL)
                .until(() -> !redis.hasKey(HoldKeys.hold(SHOW, A1))
                        && !redis.hasKey(HoldKeys.hold(SHOW, A2)));
    }

    @Test
    void theReverseIndexExpiresAlongsideTheHolds() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1));

        await().atMost(WELL_PAST_THE_TTL)
                .until(() -> !redis.hasKey(HoldKeys.bookingHolds(booking)));
    }

    @Test
    void anExpiredSeatCanBeHeldByAnotherBooking() {
        seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1));

        await().atMost(WELL_PAST_THE_TTL).until(() -> !redis.hasKey(HoldKeys.hold(SHOW, A1)));

        assertThat(seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1)).granted()).isTrue();
    }

    @Test
    void releasingAnExpiredHoldIsANoOp() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1));

        await().atMost(WELL_PAST_THE_TTL).until(() -> !redis.hasKey(HoldKeys.hold(SHOW, A1)));

        assertThat(seatHoldService.release(booking)).isZero();
    }

}
