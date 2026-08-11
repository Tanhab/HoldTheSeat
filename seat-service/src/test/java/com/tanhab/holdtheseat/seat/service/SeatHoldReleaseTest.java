package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldReleaseTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

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
    void freesTheSeatsAndDropsTheIndex() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        long released = seatHoldService.release(booking);

        assertThat(released).isEqualTo(2);
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A2))).isFalse();
        assertThat(redis.hasKey(HoldKeys.bookingHolds(booking))).isFalse();
    }

    @Test
    void releasedSeatsAreImmediatelyAvailableToAnotherBooking() {
        UUID first = UUID.randomUUID();
        seatHoldService.hold(SHOW, first, List.of(A1));
        seatHoldService.release(first);

        assertThat(seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1)).granted()).isTrue();
    }

    @Test
    void releasingTwiceIsANoOp() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1));
        seatHoldService.release(booking);

        assertThat(seatHoldService.release(booking)).isZero();
    }

    @Test
    void releasingABookingThatNeverHeldAnythingIsANoOp() {
        assertThat(seatHoldService.release(UUID.randomUUID())).isZero();
    }

    @Test
    void staleReleaseDoesNotTakeASeatSomeoneElseHasReclaimed() {
        UUID first = UUID.randomUUID();
        seatHoldService.hold(SHOW, first, List.of(A1));
        // The hold lapses while its reverse index survives — what an expiry looks like from
        // the outside, and the state a late cancellation arrives into.
        redis.delete(HoldKeys.hold(SHOW, A1));

        UUID second = UUID.randomUUID();
        seatHoldService.hold(SHOW, second, List.of(A1));
        long released = seatHoldService.release(first);

        assertThat(released).isZero();
        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, A1))).isEqualTo(second.toString());
    }

    @Test
    void releasesTheSeatsStillOwnedEvenWhenOneWasReclaimed() {
        UUID first = UUID.randomUUID();
        seatHoldService.hold(SHOW, first, List.of(A1, A2));
        redis.delete(HoldKeys.hold(SHOW, A1));
        seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1));

        long released = seatHoldService.release(first);

        assertThat(released).isEqualTo(1);
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A2))).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isTrue();
    }

}
