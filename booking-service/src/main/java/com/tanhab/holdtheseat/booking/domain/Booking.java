package com.tanhab.holdtheseat.booking.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record Booking(
        UUID id,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        long amountCents,
        BookingStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
