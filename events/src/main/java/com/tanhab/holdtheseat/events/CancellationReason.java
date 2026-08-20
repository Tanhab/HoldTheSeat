package com.tanhab.holdtheseat.events;

/**
 * Why a booking was cancelled. Recorded on the booking row as well as carried on the event,
 * so the two failure paths stay distinguishable after the fact.
 */
public enum CancellationReason {

    /** The seats could never be held, so no work was done that needs undoing. */
    SEATS_REJECTED,

    /** The seats were held and the card was declined, so the hold must be released. */
    PAYMENT_FAILED

}
