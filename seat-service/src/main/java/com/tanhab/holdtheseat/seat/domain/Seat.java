package com.tanhab.holdtheseat.seat.domain;

import java.util.UUID;

public record Seat(
        UUID id,
        UUID showId,
        String seatRow,
        int seatNumber,
        long priceCents,
        SeatStatus status
) {
}
