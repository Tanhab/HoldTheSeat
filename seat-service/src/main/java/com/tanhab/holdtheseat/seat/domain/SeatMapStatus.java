package com.tanhab.holdtheseat.seat.domain;

/**
 * What the live seat map reports. Distinct from {@link SeatStatus}: Postgres only stores
 * AVAILABLE/SOLD; HELD exists only while a Redis hold key is present.
 */
public enum SeatMapStatus {
    AVAILABLE,
    HELD,
    SOLD
}
