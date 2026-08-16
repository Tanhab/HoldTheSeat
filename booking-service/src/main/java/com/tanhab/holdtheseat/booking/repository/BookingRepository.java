package com.tanhab.holdtheseat.booking.repository;

import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingRepository {

    private static final String COLUMNS =
            "id, show_id, seat_ids, customer_id, amount_cents, status, created_at, updated_at";

    private static final RowMapper<Booking> BOOKING_MAPPER = (rs, rowNum) -> new Booking(
            rs.getObject("id", UUID.class),
            rs.getObject("show_id", UUID.class),
            toUuidList(rs.getArray("seat_ids")),
            rs.getString("customer_id"),
            // getLong would turn a NULL amount into 0, which reads as free rather than unknown.
            rs.getObject("amount_cents", Long.class),
            BookingStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcClient jdbcClient;

    public BookingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Booking insert(UUID showId, List<UUID> seatIds, String customerId) {
        return jdbcClient.sql("""
                        INSERT INTO bookings (show_id, seat_ids, customer_id, status)
                        VALUES (:showId, :seatIds, :customerId, :status)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("showId", showId)
                .param("seatIds", seatIds.toArray(UUID[]::new))
                .param("customerId", customerId)
                .param("status", BookingStatus.PENDING.name())
                .query(BOOKING_MAPPER)
                .single();
    }

    /**
     * Guarded by {@code status = 'PENDING'} so a redelivered authorization moves nothing and
     * reports 0, which is what makes the return value a truthful count of what this call
     * changed.
     *
     * @return 1 when this call confirmed the booking, 0 when it was already past PENDING
     */
    public int confirm(UUID bookingId, long amountCents) {
        return jdbcClient.sql("""
                        UPDATE bookings
                        SET status = :status, amount_cents = :amountCents, updated_at = now()
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("status", BookingStatus.CONFIRMED.name())
                .param("amountCents", amountCents)
                .param("id", bookingId)
                .update();
    }

    public Optional<Booking> findById(UUID id) {
        return jdbcClient.sql("SELECT %s FROM bookings WHERE id = :id".formatted(COLUMNS))
                .param("id", id)
                .query(BOOKING_MAPPER)
                .optional();
    }

    private static List<UUID> toUuidList(Array array) throws SQLException {
        return List.of((UUID[]) array.getArray());
    }

}
