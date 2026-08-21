package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nobody decided this booking was dead — the payment window ran out with no answer.
 * Distinct from {@link BookingCancelled}: a cancellation is something choosing to kill
 * the booking; an expiry is silence. Carries {@code showId} and {@code seatIds} so the
 * seat service can release whatever is still held without reading Redis key names.
 */
public record BookingExpired(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "BookingExpired";
    public static final int SCHEMA_VERSION = 1;

    public static BookingExpired of(UUID bookingId, UUID showId, List<UUID> seatIds) {
        return new BookingExpired(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.BOOKINGS;
    }

}
