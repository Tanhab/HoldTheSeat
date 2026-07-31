package com.tanhab.holdtheseat.booking.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID id) {
        super("Booking %s not found".formatted(id));
    }

}
