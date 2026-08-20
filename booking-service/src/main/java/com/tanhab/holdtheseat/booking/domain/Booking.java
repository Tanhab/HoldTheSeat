package com.tanhab.holdtheseat.booking.domain;

import com.tanhab.holdtheseat.events.CancellationReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record Booking(
        UUID id,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        // Null while PENDING: the seat service owns prices and has not answered yet.
        Long amountCents,
        BookingStatus status,
        // Null unless the booking was cancelled; names which failure path did it.
        CancellationReason cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {
}
