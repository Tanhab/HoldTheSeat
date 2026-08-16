package com.tanhab.holdtheseat.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * No amount: seat prices belong to the seat service, so a client naming its own total would
 * be a client setting its own price. The figure arrives later, on {@code SeatsHeld}.
 */
public record CreateBookingRequest(

        @NotNull
        UUID showId,

        @NotEmpty
        List<@NotNull UUID> seatIds,

        @NotBlank
        String customerId
) {
}
