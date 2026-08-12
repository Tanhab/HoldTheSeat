package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The booking is paid and final. Carries {@code showId} and {@code seatIds} so the seat
 * service can settle the hold straight from the event rather than reading them back out of
 * Redis key names.
 */
public record BookingConfirmed(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "BookingConfirmed";
    public static final int SCHEMA_VERSION = 1;

    public static BookingConfirmed of(UUID bookingId, UUID showId, List<UUID> seatIds) {
        return new BookingConfirmed(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.BOOKINGS;
    }

}
