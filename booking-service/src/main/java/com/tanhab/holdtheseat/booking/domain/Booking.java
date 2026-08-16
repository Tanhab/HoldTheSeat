package com.tanhab.holdtheseat.booking.domain;

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
        Instant createdAt,
        Instant updatedAt
) {
}
