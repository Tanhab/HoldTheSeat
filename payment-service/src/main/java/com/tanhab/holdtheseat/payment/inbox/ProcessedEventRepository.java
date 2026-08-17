package com.tanhab.holdtheseat.payment.inbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbcClient;

    public ProcessedEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Claims an event for this consumer group, inside the caller's transaction.
     *
     * @return true when this call is the first to see it and the work should run, false when
     * it has already been handled
     */
    public boolean claim(String consumerGroup, UUID eventId) {
        int rows = jdbcClient.sql("""
                        INSERT INTO processed_events (consumer_group, event_id)
                        VALUES (:consumerGroup, :eventId) ON CONFLICT (consumer_group, event_id) DO NOTHING
                        """)
                .param("consumerGroup", consumerGroup)
                .param("eventId", eventId)
                .update();
        return rows == 1;
    }

}
