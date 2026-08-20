package com.tanhab.holdtheseat.events;

/**
 * Why the seat service refused to hold. Part of the wire contract, so a value is never
 * removed or renamed once it has been published — a consumer reading an unknown name fails
 * loudly rather than guessing.
 */
public enum RejectionReason {

    /** Some requested seat is already sold or held by another booking. */
    SEATS_UNAVAILABLE,

    /** The show id names nothing this service knows about. */
    UNKNOWN_SHOW,

    /** One or more seat ids do not belong to the requested show. */
    UNKNOWN_SEATS

}
