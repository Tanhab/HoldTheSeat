package com.tanhab.holdtheseat.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(

        @NotNull
        UUID showId,

        @NotEmpty
        List<@NotNull UUID> seatIds,

        @NotBlank
        String customerId,

        @PositiveOrZero
        long amountCents
) {
}
