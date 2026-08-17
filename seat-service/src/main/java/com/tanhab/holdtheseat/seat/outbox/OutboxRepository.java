package com.tanhab.holdtheseat.seat.outbox;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private static final RowMapper<OutboxRecord> OUTBOX_MAPPER = (rs, rowNum) -> new OutboxRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("aggregate_id", UUID.class),
            rs.getString("topic"),
            rs.getString("payload")
    );

    private final JdbcClient jdbcClient;

    public OutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Called inside the caller's transaction, never on its own.
     */
    public void append(UUID aggregateId, String eventType, String topic, String payload) {
        // A String parameter binds as text and Postgres will not coerce it to jsonb.
        //
        // jsonb stores a parsed form, so what comes back out is re-rendered: whitespace
        // normalised and keys reordered. The bytes are not preserved and the meaning is.
        // Worth it for the validation on insert — a malformed payload fails here, inside the
        // transaction, rather than in the poller with the booking already committed.
        jdbcClient.sql("""
                        INSERT INTO outbox (aggregate_id, event_type, topic, payload)
                        VALUES (:aggregateId, :eventType, :topic, CAST(:payload AS jsonb))
                        """)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("topic", topic)
                .param("payload", payload)
                .update();
    }

    /**
     * Oldest unpublished rows, locked so a second poller takes a different batch.
     *
     * <p>{@code SKIP LOCKED} is what makes this a work queue rather than a bottleneck: a
     * concurrent poller steps over the locked rows and claims the next ones instead of
     * blocking until this transaction commits. The locks live for the caller's transaction,
     * so claiming outside one releases them immediately and guarantees nothing.
     */
    public List<OutboxRecord> claimUnpublished(int limit) {
        return jdbcClient.sql("""
                        SELECT id, aggregate_id, topic, payload::text AS payload
                        FROM outbox
                        WHERE published_at IS NULL
                        ORDER BY created_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("limit", limit)
                .query(OUTBOX_MAPPER)
                .list();
    }

    public void markPublished(List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }

        // = ANY(array) rather than IN (:ids): one query plan whatever the batch size.
        jdbcClient.sql("UPDATE outbox SET published_at = now() WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(UUID[]::new))
                .update();
    }

}
