package com.tanhab.holdtheseat.seat.exception;

import java.util.UUID;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(UUID showId) {
        super("No show with id " + showId);
    }

}
