package com.tanhab.holdtheseat.booking.dto;

import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import com.tanhab.holdtheseat.events.CancellationReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        // Null until the seat service prices the held seats.
        Long amountCents,
        BookingStatus status,
        CancellationReason cancellationReason,
        Instant createdAt
) {
}
