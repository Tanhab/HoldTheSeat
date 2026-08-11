package com.tanhab.holdtheseat.seat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param heldUntil when the hold lapses unless it is confirmed — the caller's payment window
 */
public record HoldResponse(
        UUID bookingId,
        UUID showId,
        List<UUID> seatIds,
        Instant heldUntil
) {
}
