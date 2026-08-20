package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every requested seat is held, and this is what they cost. The seat service owns
 * {@code price_cents}, so it is the service that states the amount the payment service
 * will charge.
 *
 * <p>{@code customerId} is the booking service's, passed through untouched: the seat
 * service has no use for it, and the service that charges the card needs to know who it
 * is charging.
 *
 * <p>{@code holdExpiresAt} is the payment window closing — the moment the Redis keys lapse
 * and the seats free themselves.
 */
public record SeatsHeld(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        long amountCents,
        Instant holdExpiresAt,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "SeatsHeld";
    // Version 2 added customerId. A version 1 payload still reads, leaving it null, which
    // is why the bump needed no migration and no coordinated deploy.
    public static final int SCHEMA_VERSION = 2;

    public static SeatsHeld of(UUID bookingId, UUID showId, List<UUID> seatIds, String customerId,
                               long amountCents, Instant holdExpiresAt) {
        return new SeatsHeld(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), customerId, amountCents,
                holdExpiresAt, Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.SEATS;
    }

}
