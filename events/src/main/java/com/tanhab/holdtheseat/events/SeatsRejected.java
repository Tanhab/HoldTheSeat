package com.tanhab.holdtheseat.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The seats could not be held. Nothing was written, so nothing needs undoing — that is what
 * separates this from a payment failure, and it is why the booking that receives it cancels
 * without any release ever happening.
 *
 * <p>{@code conflictingSeatIds} names the seats that blocked the request, and is empty when
 * the request was refused for a reason other than a clash.
 */
public record SeatsRejected(
        UUID eventId,
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        RejectionReason reason,
        List<UUID> conflictingSeatIds,
        Instant occurredAt,
        int schemaVersion
) implements DomainEvent {

    public static final String TYPE = "SeatsRejected";
    public static final int SCHEMA_VERSION = 1;

    public static SeatsRejected seatsUnavailable(UUID bookingId, UUID showId, List<UUID> seatIds,
                                                 List<UUID> conflictingSeatIds) {
        return new SeatsRejected(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds),
                RejectionReason.SEATS_UNAVAILABLE, List.copyOf(conflictingSeatIds),
                Instant.now(), SCHEMA_VERSION);
    }

    /**
     * The request named a show or seats this service does not have. Permanent: retrying it
     * would fail identically forever, so it is answered rather than thrown.
     */
    public static SeatsRejected invalidRequest(UUID bookingId, UUID showId, List<UUID> seatIds,
                                               RejectionReason reason) {
        return new SeatsRejected(
                UUID.randomUUID(), bookingId, showId, List.copyOf(seatIds), reason, List.of(),
                Instant.now(), SCHEMA_VERSION);
    }

    @Override
    public String topic() {
        return Topics.SEATS;
    }

}
