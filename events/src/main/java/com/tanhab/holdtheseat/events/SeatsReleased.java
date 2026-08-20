package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The compensating transaction completed: these seats are available again. Announced only
 * when holds were actually freed, so its presence is a truthful signal that work was undone
 * rather than that a cancellation was merely seen.
 *
 * <p>{@code releasedCount} can be smaller than {@code seatIds} when part of the hold had
 * already lapsed, or when a seat was legitimately reclaimed by another booking in the
 * meantime.
 */
public record SeatsReleased(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        long releasedCount,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "SeatsReleased";
    public static final int SCHEMA_VERSION = 1;

    public static SeatsReleased of(UUID bookingId, UUID showId, List<UUID> seatIds, long releasedCount) {
        return new SeatsReleased(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), releasedCount,
                Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.SEATS;
    }

}
