package com.tanhab.holdtheseat.seat.exception;

import java.util.List;
import java.util.UUID;

public class UnknownSeatException extends RuntimeException {

    private final List<UUID> seatIds;

    public UnknownSeatException(UUID showId, List<UUID> seatIds) {
        super("Seats %s do not belong to show %s".formatted(seatIds, showId));
        this.seatIds = List.copyOf(seatIds);
    }

    public List<UUID> seatIds() {
        return seatIds;
    }

}
