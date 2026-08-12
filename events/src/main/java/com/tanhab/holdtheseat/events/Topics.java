package com.tanhab.holdtheseat.events;

/**
 * One topic per service that produces to it. No service subscribes to a topic it writes to,
 * so no listener ever has to filter out its own events.
 */
public final class Topics {

    public static final String BOOKINGS = "bookings";
    public static final String SEATS = "seats";
    public static final String PAYMENTS = "payments";

    public static final int PARTITIONS = 3;

    private Topics() {
    }

}
