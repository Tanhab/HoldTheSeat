package com.tanhab.holdtheseat.seat.hold;

import java.util.UUID;

/**
 * The only place Redis key names are built.
 *
 * <p>The braces are a Redis Cluster hash tag: only the text inside them decides the slot,
 * so every key for one show lands on one node. A multi-key Lua script requires that —
 * without it the server rejects the call with CROSSSLOT. Single-node Redis ignores hash
 * tags entirely, so this costs nothing today and makes cluster mode a config change.
 */
public final class HoldKeys {

    private HoldKeys() {
    }

    public static String hold(UUID showId, UUID seatId) {
        return "hold:{%s}:%s".formatted(showId, seatId);
    }

    public static String sold(UUID showId) {
        return "sold:{%s}".formatted(showId);
    }

    public static String bookingHolds(UUID bookingId) {
        return "booking_holds:{%s}".formatted(bookingId);
    }

}
