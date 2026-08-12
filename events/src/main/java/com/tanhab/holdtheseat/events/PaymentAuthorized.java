package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The card was charged. {@code gatewayRef} is the mock gateway's reference, kept because a
 * real one would be the only handle a refund could be issued against.
 */
public record PaymentAuthorized(
        UUID eventId,
        UUID bookingId,
        long amountCents,
        String gatewayRef,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "PaymentAuthorized";
    public static final int SCHEMA_VERSION = 1;

    public static PaymentAuthorized of(UUID bookingId, long amountCents, String gatewayRef) {
        return new PaymentAuthorized(
                UUID.randomUUID(), bookingId, amountCents, gatewayRef, Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.PAYMENTS;
    }

}
