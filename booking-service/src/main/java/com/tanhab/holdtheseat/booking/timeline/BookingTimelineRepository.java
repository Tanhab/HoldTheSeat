package com.tanhab.holdtheseat.booking.timeline;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class BookingTimelineRepository {

    private static final RowMapper<BookingTimelineEntry> ENTRY_MAPPER = (rs, rowNum) -> new BookingTimelineEntry(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getString("event_type"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("payload"),
            rs.getTimestamp("recorded_at").toInstant()
    );

    private final JdbcClient jdbcClient;

    public BookingTimelineRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Same transaction as the outbox / inbox work that produced or saw this event.
     * {@code UNIQUE (booking_id, event_id)} is the second defence against duplicates.
     */
    public void append(UUID bookingId, UUID eventId, String eventType, Instant occurredAt, String payload) {
        jdbcClient.sql("""
                        INSERT INTO booking_timeline (booking_id, event_id, event_type, occurred_at, payload)
                        VALUES (:bookingId, :eventId, :eventType, :occurredAt, CAST(:payload AS jsonb))
                        """)
                .param("bookingId", bookingId)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("occurredAt", Timestamp.from(occurredAt))
                .param("payload", payload)
                .update();
    }

    /**
     * Ordered by occurred_at, then recorded_at as tiebreak (cross-topic clocks are not ordered).
     */
    public List<BookingTimelineEntry> findByBookingId(UUID bookingId) {
        return jdbcClient.sql("""
                        SELECT id, booking_id, event_id, event_type,
                               occurred_at, payload::text AS payload, recorded_at
                        FROM booking_timeline
                        WHERE booking_id = :bookingId
                        ORDER BY occurred_at ASC, recorded_at ASC
                        """)
                .param("bookingId", bookingId)
                .query(ENTRY_MAPPER)
                .list();
    }

}
