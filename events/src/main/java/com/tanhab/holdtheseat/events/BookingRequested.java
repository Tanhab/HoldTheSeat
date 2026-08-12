package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A customer asked for these seats. Carries no amount: the booking service does not know
 * what the seats cost, because prices belong to the seat service.
 */
public record BookingRequested(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "BookingRequested";
    public static final int SCHEMA_VERSION = 1;

    public static BookingRequested of(UUID bookingId, UUID showId, List<UUID> seatIds, String customerId) {
        return new BookingRequested(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), customerId,
                Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.BOOKINGS;
    }

}
