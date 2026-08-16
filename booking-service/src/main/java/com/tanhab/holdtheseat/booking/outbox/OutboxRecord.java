package com.tanhab.holdtheseat.booking.outbox;

import java.util.UUID;

/**
 * One unpublished row, carrying everything the poller needs to send it and nothing else.
 *
 * @param aggregateId the booking id, which is also the Kafka message key
 * @param payload     the event, serialised when the transaction was written
 */
public record OutboxRecord(UUID id, UUID aggregateId, String topic, String payload) {
}
