package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.SeatMapStatus;
import com.tanhab.holdtheseat.seat.dto.SeatMapEntry;
import com.tanhab.holdtheseat.seat.dto.SeatMapResponse;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatMapTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID A3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    @Autowired
    private SeatMapService seatMapService;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private SeatSettlementService settlementService;

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
        restoreSeats();
    }

    @AfterEach
    void releaseSeatsForOtherTests() {
        restoreSeats();
    }

    private void restoreSeats() {
        jdbcClient.sql("UPDATE seats SET status = 'AVAILABLE' WHERE show_id = :showId")
                .param("showId", SHOW)
                .update();
    }

    @Test
    void unknownShowIsNotFound() {
        UUID missing = UUID.fromString("88888888-8888-8888-8888-888888888888");

        assertThatThrownBy(() -> seatMapService.mapForShow(missing))
                .isInstanceOf(ShowNotFoundException.class);
    }

    @Test
    void freshShowReportsEverySeatAvailable() {
        SeatMapResponse map = seatMapService.mapForShow(SHOW);

        assertThat(map.showId()).isEqualTo(SHOW);
        assertThat(map.seats()).isNotEmpty();
        assertThat(map.seats()).allMatch(seat -> seat.status() == SeatMapStatus.AVAILABLE);
    }

    @Test
    void heldSeatsReadAsHeldOthersStayAvailable() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1, A2));

        Map<UUID, SeatMapEntry> byId = index(seatMapService.mapForShow(SHOW));

        assertThat(byId.get(A1).status()).isEqualTo(SeatMapStatus.HELD);
        assertThat(byId.get(A2).status()).isEqualTo(SeatMapStatus.HELD);
        assertThat(byId.get(A3).status()).isEqualTo(SeatMapStatus.AVAILABLE);
    }

    @Test
    void settledSeatsReadAsSoldEvenIfAHoldKeyLingers() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1));
        settlementService.settle(BookingConfirmed.of(booking, SHOW, List.of(A1)));

        // Stray key after a messy crash — durable SOLD still wins on the map.
        redis.opsForValue().set(HoldKeys.hold(SHOW, A1), booking.toString());

        Map<UUID, SeatMapEntry> byId = index(seatMapService.mapForShow(SHOW));

        assertThat(byId.get(A1).status()).isEqualTo(SeatMapStatus.SOLD);
    }

    @Test
    void releasingAHoldReturnsSeatsToAvailable() {
        UUID booking = UUID.randomUUID();
        seatHoldService.hold(SHOW, booking, List.of(A1, A2));
        seatHoldService.release(booking);

        Map<UUID, SeatMapEntry> byId = index(seatMapService.mapForShow(SHOW));

        assertThat(byId.get(A1).status()).isEqualTo(SeatMapStatus.AVAILABLE);
        assertThat(byId.get(A2).status()).isEqualTo(SeatMapStatus.AVAILABLE);
    }

    private static Map<UUID, SeatMapEntry> index(SeatMapResponse map) {
        return map.seats().stream().collect(Collectors.toMap(SeatMapEntry::id, Function.identity()));
    }

}
