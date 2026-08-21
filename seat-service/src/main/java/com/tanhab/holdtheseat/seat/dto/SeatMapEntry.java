package com.tanhab.holdtheseat.seat.dto;

import com.tanhab.holdtheseat.seat.domain.SeatMapStatus;

import java.util.UUID;

public record SeatMapEntry(
        UUID id,
        String row,
        int number,
        long priceCents,
        SeatMapStatus status
) {
}
