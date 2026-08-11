package com.tanhab.holdtheseat.seat.repository;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SeatRepositoryTest extends AbstractIntegrationTest {

    private static final UUID DEMO_SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT_A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SEAT_A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID SEAT_B5 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000005");

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void findByShowIdReturnsTheWholeMapInPositionOrder() {
        List<Seat> seats = seatRepository.findByShowId(DEMO_SHOW);

        assertThat(seats).hasSize(10);
        assertThat(seats.getFirst().id()).isEqualTo(SEAT_A1);
        assertThat(seats.getFirst().seatRow()).isEqualTo("A");
        assertThat(seats.getFirst().seatNumber()).isEqualTo(1);
        assertThat(seats.getFirst().priceCents()).isEqualTo(5000L);
        assertThat(seats.getFirst().status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seats.getLast().id()).isEqualTo(SEAT_B5);
    }

    @Test
    void findByIdsReturnsOnlyTheRequestedSeats() {
        List<Seat> seats = seatRepository.findByIds(DEMO_SHOW, List.of(SEAT_A1, SEAT_B5));

        assertThat(seats).extracting(Seat::id).containsExactly(SEAT_A1, SEAT_B5);
    }

    @Test
    void findByIdsExcludesSeatsBelongingToAnotherShow() {
        UUID otherShow = insertShow("Some Other Show");
        UUID foreignSeat = insertSeat(otherShow, "A", 1);

        List<Seat> seats = seatRepository.findByIds(DEMO_SHOW, List.of(SEAT_A1, foreignSeat));

        assertThat(seats).extracting(Seat::id).containsExactly(SEAT_A1);
    }

    @Test
    void findByIdsReturnsEmptyWhenNothingMatches() {
        assertThat(seatRepository.findByIds(DEMO_SHOW, List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void findSoldSeatIdsIsEmptyUntilSomethingSells() {
        assertThat(seatRepository.findSoldSeatIds(DEMO_SHOW)).isEmpty();
    }

    @Test
    void markSoldMovesSeatsAndTheyBecomeVisibleAsSold() {
        int updated = seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1, SEAT_A2));

        assertThat(updated).isEqualTo(2);
        assertThat(seatRepository.findSoldSeatIds(DEMO_SHOW))
                .containsExactlyInAnyOrder(SEAT_A1, SEAT_A2);
    }

    @Test
    void markSoldIsIdempotentAndReportsOnlyWhatItChanged() {
        seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1));

        int secondCall = seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1, SEAT_A2));

        assertThat(secondCall).isEqualTo(1);
        assertThat(seatRepository.findSoldSeatIds(DEMO_SHOW))
                .containsExactlyInAnyOrder(SEAT_A1, SEAT_A2);
    }

    @Test
    void markSoldIgnoresSeatsFromAnotherShow() {
        UUID otherShow = insertShow("Some Other Show");
        UUID foreignSeat = insertSeat(otherShow, "A", 1);

        int updated = seatRepository.markSold(DEMO_SHOW, List.of(foreignSeat));

        assertThat(updated).isZero();
        assertThat(seatRepository.findSoldSeatIds(otherShow)).isEmpty();
    }

    private UUID insertShow(String name) {
        return jdbcClient.sql("INSERT INTO shows (name, starts_at) VALUES (:name, now()) RETURNING id")
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID insertSeat(UUID showId, String row, int number) {
        return jdbcClient.sql("""
                        INSERT INTO seats (show_id, seat_row, seat_number, price_cents)
                        VALUES (:showId, :row, :number, 1000)
                        RETURNING id
                        """)
                .param("showId", showId)
                .param("row", row)
                .param("number", number)
                .query(UUID.class)
                .single();
    }

}
