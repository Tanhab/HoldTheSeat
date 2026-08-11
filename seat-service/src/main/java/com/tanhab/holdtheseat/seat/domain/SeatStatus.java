package com.tanhab.holdtheseat.seat.domain;

/**
 * Durable seat state. A hold is not represented here — it exists only as a Redis key with
 * a TTL, so the database cannot express one.
 */
public enum SeatStatus {
    AVAILABLE,
    SOLD
}
