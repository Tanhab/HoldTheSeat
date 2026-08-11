package com.tanhab.holdtheseat.seat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HoldSeatsRequest(

        @NotNull
        UUID showId,

        @NotNull
        UUID bookingId,

        @NotEmpty
        List<@NotNull UUID> seatIds
) {
}
