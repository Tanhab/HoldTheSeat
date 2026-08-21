package com.tanhab.holdtheseat.seat.dto;

import java.util.List;
import java.util.UUID;

public record SeatMapResponse(
        UUID showId,
        List<SeatMapEntry> seats
) {
}
