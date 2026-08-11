package com.tanhab.holdtheseat.seat.hold;

import java.util.List;
import java.util.UUID;

/**
 * @param granted            whether every requested seat was held
 * @param conflictingSeatIds the seats that blocked it, empty when granted
 */
public record HoldOutcome(boolean granted, List<UUID> conflictingSeatIds) {

    public static HoldOutcome allSeatsHeld() {
        return new HoldOutcome(true, List.of());
    }

    public static HoldOutcome blockedBy(List<UUID> conflictingSeatIds) {
        return new HoldOutcome(false, List.copyOf(conflictingSeatIds));
    }

}
