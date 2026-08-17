package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.events.BookingConfirmed;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatSettlementServiceTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private SeatSettlementService settlementService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetState() {
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbcClient.sql("UPDATE seats SET status = 'AVAILABLE' WHERE show_id = :showId")
                .param("showId", SHOW)
                .update();
    }

    @Test
    void aHeldSeatBecomesSoldInBothStores() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1, A2));

        settlementService.settle(BookingConfirmed.of(bookingId, SHOW, List.of(A1, A2)));

        assertThat(statusOf(A1)).isEqualTo(SeatStatus.SOLD);
        assertThat(statusOf(A2)).isEqualTo(SeatStatus.SOLD);
        assertThat(redis.opsForSet().isMember(HoldKeys.sold(SHOW), A1.toString())).isTrue();
        assertThat(redis.opsForSet().isMember(HoldKeys.sold(SHOW), A2.toString())).isTrue();
    }

    @Test
    void settlingDropsTheHoldsAndTheReverseIndex() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1));

        settlementService.settle(BookingConfirmed.of(bookingId, SHOW, List.of(A1)));

        assertThat(redis.hasKey(HoldKeys.hold(SHOW, A1))).isFalse();
        assertThat(redis.hasKey(HoldKeys.bookingHolds(bookingId))).isFalse();
    }

    /**
     * A sold seat must never be holdable again, which is the point of the sold set: the claim
     * script checks it, and it survives the hold key being gone.
     */
    @Test
    void aSoldSeatCannotBeHeldAgain() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1));
        settlementService.settle(BookingConfirmed.of(bookingId, SHOW, List.of(A1)));

        assertThat(seatHoldService.hold(SHOW, UUID.randomUUID(), List.of(A1)).granted()).isFalse();
    }

    /**
     * Hard problem #6's minimum bar: payment authorised after the TTL lapsed. The seat must
     * stay AVAILABLE rather than be sold to a booking that no longer holds it.
     */
    @Test
    void aConfirmationForALapsedHoldSellsNothing() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1));
        seatHoldService.release(bookingId);

        settlementService.settle(BookingConfirmed.of(bookingId, SHOW, List.of(A1)));

        assertThat(statusOf(A1)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(redis.opsForSet().isMember(HoldKeys.sold(SHOW), A1.toString())).isFalse();
    }

    /**
     * The same lapse, but the seat has since been taken by someone else. Settling would sell
     * a seat out from under its current holder.
     */
    @Test
    void aConfirmationForASeatNowHeldByAnotherBookingSellsNothing() {
        UUID first = UUID.randomUUID();
        seatHoldService.hold(SHOW, first, List.of(A1));
        seatHoldService.release(first);

        UUID second = UUID.randomUUID();
        seatHoldService.hold(SHOW, second, List.of(A1));

        settlementService.settle(BookingConfirmed.of(first, SHOW, List.of(A1)));

        assertThat(statusOf(A1)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, A1))).isEqualTo(second.toString());
    }

    /**
     * All-or-nothing survives into settlement: one lapsed seat out of two means neither sells.
     */
    @Test
    void aPartiallyLapsedHoldSellsNothing() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1, A2));
        redis.delete(HoldKeys.hold(SHOW, A2));

        settlementService.settle(BookingConfirmed.of(bookingId, SHOW, List.of(A1, A2)));

        assertThat(statusOf(A1)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(statusOf(A2)).isEqualTo(SeatStatus.AVAILABLE);
    }

    /**
     * Redelivery reaches here as a repeat call, so settling twice must not throw or undo.
     */
    @Test
    void settlingTwiceLeavesTheSeatSold() {
        UUID bookingId = UUID.randomUUID();
        seatHoldService.hold(SHOW, bookingId, List.of(A1));
        BookingConfirmed confirmed = BookingConfirmed.of(bookingId, SHOW, List.of(A1));

        settlementService.settle(confirmed);
        settlementService.settle(confirmed);

        assertThat(statusOf(A1)).isEqualTo(SeatStatus.SOLD);
        assertThat(redis.opsForSet().isMember(HoldKeys.sold(SHOW), A1.toString())).isTrue();
    }

    private SeatStatus statusOf(UUID seatId) {
        return seatRepository.findByIds(SHOW, List.of(seatId)).stream()
                .map(Seat::status)
                .findFirst()
                .orElseThrow();
    }

}
