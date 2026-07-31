package com.tanhab.holdtheseat.booking.dto;

import com.tanhab.holdtheseat.booking.domain.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID showId,
        List<UUID> seatIds,
        String customerId,
        long amountCents,
        BookingStatus status,
        Instant createdAt
) {
}
