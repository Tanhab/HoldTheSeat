package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.exception.UnknownSeatException;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatHoldServiceTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID A3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
    private static final UUID B1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

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
    void grantsAHoldOnFreeSeats() {
        HoldOutcome outcome = seatHoldService.hold(SHOW, bookingId(), List.of(A1, A2));

        assertThat(outcome.granted()).isTrue();
        assertThat(outcome.conflictingSeatIds()).isEmpty();
    }

    @Test
    void writesTheHoldKeysAndTheReverseIndex() {
        UUID booking = bookingId();

        seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, A1))).isEqualTo(booking.toString());
        assertThat(redis.opsForSet().members(HoldKeys.bookingHolds(booking)))
                .containsExactlyInAnyOrder(HoldKeys.hold(SHOW, A1), HoldKeys.hold(SHOW, A2));
        assertThat(redis.getExpire(HoldKeys.bookingHolds(booking))).isPositive();
    }

    @Test
    void refusesASeatAnotherBookingHolds() {
        seatHoldService.hold(SHOW, bookingId(), List.of(A1));

        HoldOutcome outcome = seatHoldService.hold(SHOW, bookingId(), List.of(A1));

        assertThat(outcome.granted()).isFalse();
        assertThat(outcome.conflictingSeatIds()).containsExactly(A1);
    }

    @Test
    void writesNothingWhenAnySeatInTheRequestIsTaken() {
        seatHoldService.hold(SHOW, bookingId(), List.of(A3));
        UUID loser = bookingId();

        HoldOutcome outcome = seatHoldService.hold(SHOW, loser, List.of(A1, A2, A3, B1));

        assertThat(outcome.granted()).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A2))).isFalse();
        assertThat(redis.hasKey(HoldKeys.hold(SHOW, B1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.bookingHolds(loser))).isFalse();
    }

    @Test
    void namesEverySeatThatClashedNotJustTheFirst() {
        seatHoldService.hold(SHOW, bookingId(), List.of(A1));
        seatHoldService.hold(SHOW, bookingId(), List.of(A3));

        HoldOutcome outcome = seatHoldService.hold(SHOW, bookingId(), List.of(A1, A2, A3));

        assertThat(outcome.conflictingSeatIds()).containsExactlyInAnyOrder(A1, A3);
    }

    @Test
    void grantsWhenTheSameBookingAsksAgain() {
        UUID booking = bookingId();
        seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        HoldOutcome retry = seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        assertThat(retry.granted()).isTrue();
    }

    @Test
    void refusesASeatThatIsAlreadySold() {
        redis.opsForSet().add(HoldKeys.sold(SHOW), A1.toString());

        HoldOutcome outcome = seatHoldService.hold(SHOW, bookingId(), List.of(A1, A2));

        assertThat(outcome.granted()).isFalse();
        assertThat(outcome.conflictingSeatIds()).containsExactly(A1);
    }

    @Test
    void refusesASoldSeatEvenToTheBookingHoldingIt() {
        UUID booking = bookingId();
        seatHoldService.hold(SHOW, booking, List.of(A1));
        redis.opsForSet().add(HoldKeys.sold(SHOW), A1.toString());

        HoldOutcome outcome = seatHoldService.hold(SHOW, booking, List.of(A1));

        assertThat(outcome.granted()).isFalse();
    }

    @Test
    void rejectsAnUnknownShow() {
        assertThatThrownBy(() -> seatHoldService.hold(UUID.randomUUID(), bookingId(), List.of(A1)))
                .isInstanceOf(ShowNotFoundException.class);
    }

    @Test
    void rejectsASeatThatBelongsToNoShow() {
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> seatHoldService.hold(SHOW, bookingId(), List.of(A1, stranger)))
                .isInstanceOf(UnknownSeatException.class)
                .hasMessageContaining(stranger.toString());
    }

    private static UUID bookingId() {
        return UUID.randomUUID();
    }

}
