package com.tanhab.holdtheseat.seat.repository;

import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SeatRepository {

    private static final String COLUMNS = "id, show_id, seat_row, seat_number, price_cents, status";

    private static final RowMapper<Seat> SEAT_MAPPER = (rs, rowNum) -> new Seat(
            rs.getObject("id", UUID.class),
            rs.getObject("show_id", UUID.class),
            rs.getString("seat_row"),
            rs.getInt("seat_number"),
            rs.getLong("price_cents"),
            SeatStatus.valueOf(rs.getString("status"))
    );

    private static final RowMapper<UUID> ID_MAPPER = (rs, rowNum) -> rs.getObject("id", UUID.class);

    private final JdbcClient jdbcClient;

    public SeatRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Every seat in the show, ordered by position so the seat map reads top-left to
     * bottom-right.
     */
    public List<Seat> findByShowId(UUID showId) {
        return jdbcClient.sql("""
                        SELECT %s FROM seats
                        WHERE show_id = :showId
                        ORDER BY seat_row, seat_number
                        """.formatted(COLUMNS))
                .param("showId", showId)
                .query(SEAT_MAPPER)
                .list();
    }

    /**
     * The requested seats that actually belong to this show. A caller compares the size of
     * the result against the size of the request to detect ids that do not.
     */
    public List<Seat> findByIds(UUID showId, List<UUID> seatIds) {
        // = ANY(array) rather than IN (:ids): one query plan whatever the seat count.
        return jdbcClient.sql("""
                        SELECT %s FROM seats
                        WHERE show_id = :showId AND id = ANY(:seatIds)
                        ORDER BY seat_row, seat_number
                        """.formatted(COLUMNS))
                .param("showId", showId)
                .param("seatIds", seatIds.toArray(UUID[]::new))
                .query(SEAT_MAPPER)
                .list();
    }

    public List<UUID> findSoldSeatIds(UUID showId) {
        return jdbcClient.sql("SELECT id FROM seats WHERE show_id = :showId AND status = 'SOLD'")
                .param("showId", showId)
                .query(ID_MAPPER)
                .list();
    }

    /**
     * Guarded by {@code status = 'AVAILABLE'} so a repeat call moves nothing and reports 0,
     * which is what makes the return value a truthful count of what this call changed.
     *
     * @return the number of rows actually moved to SOLD
     */
    public int markSold(UUID showId, List<UUID> seatIds) {
        return jdbcClient.sql("""
                        UPDATE seats SET status = 'SOLD'
                        WHERE show_id = :showId AND id = ANY(:seatIds) AND status = 'AVAILABLE'
                        """)
                .param("showId", showId)
                .param("seatIds", seatIds.toArray(UUID[]::new))
                .update();
    }

}
