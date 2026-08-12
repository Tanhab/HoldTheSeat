package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every requested seat is held, and this is what they cost. The seat service owns
 * {@code price_cents}, so it is the service that states the amount the payment service
 * will charge.
 *
 * <p>{@code holdExpiresAt} is the payment window closing — the moment the Redis keys lapse
 * and the seats free themselves.
 */
public record SeatsHeld(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        long amountCents,
        Instant holdExpiresAt,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "SeatsHeld";
    public static final int SCHEMA_VERSION = 1;

    public static SeatsHeld of(UUID bookingId, UUID showId, List<UUID> seatIds,
                               long amountCents, Instant holdExpiresAt) {
        return new SeatsHeld(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), amountCents,
                holdExpiresAt, Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.SEATS;
    }

}
