package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The booking is dead, and both failure paths end here. The seat service releases whatever
 * this booking still holds, which is nothing at all when the hold was refused in the first
 * place — the release is safe either way, so one event serves both paths.
 *
 * <p>Carries {@code showId} and {@code seatIds} like {@link BookingConfirmed} does, so the
 * seat service can name the seats it freed without reading them back out of Redis key names.
 */
public record BookingCancelled(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        CancellationReason reason,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "BookingCancelled";
    public static final int SCHEMA_VERSION = 1;

    public static BookingCancelled of(UUID bookingId, UUID showId, List<UUID> seatIds,
                                      CancellationReason reason) {
        return new BookingCancelled(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), reason,
                Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.BOOKINGS;
    }

}
