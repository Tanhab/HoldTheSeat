package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The card was declined. A business outcome, not an error: the seats really were held, so the
 * booking that receives this has work in another service to undo.
 *
 * <p>{@code gatewayReason} is the processor's own code, passed through untouched and never
 * branched on. A real gateway's vocabulary is its own and changes without asking.
 */
public record PaymentFailed(
        UUID eventId,
        UUID bookingId,
        long amountCents,
        String gatewayReason,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "PaymentFailed";
    public static final int SCHEMA_VERSION = 1;

    public static PaymentFailed of(UUID bookingId, long amountCents, String gatewayReason) {
        return new PaymentFailed(
                UUID.randomUUID(), bookingId, amountCents, gatewayReason, Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.PAYMENTS;
    }

}
