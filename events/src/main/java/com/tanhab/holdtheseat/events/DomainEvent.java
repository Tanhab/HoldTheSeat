package com.tanhab.holdtheseat.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * The saga's vocabulary. Every event on every topic is one of these four.
 *
 * <p>The discriminator is a {@code eventType} string written into the JSON body rather than
 * Jackson's default of a fully-qualified class name in a Kafka header. A class name on the
 * wire would make renaming a record here a breaking change for every deployed consumer; a
 * string constant makes the contract the protocol's, not the Java layout's.
 *
 * <p>Sealed so a new event type cannot be added without the compiler pointing at every
 * handler that has to decide what to do with it.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BookingRequested.class, name = BookingRequested.TYPE),
        @JsonSubTypes.Type(value = SeatsHeld.class, name = SeatsHeld.TYPE),
        @JsonSubTypes.Type(value = PaymentAuthorized.class, name = PaymentAuthorized.TYPE),
        @JsonSubTypes.Type(value = BookingConfirmed.class, name = BookingConfirmed.TYPE)
})
public sealed interface DomainEvent
        permits BookingRequested, SeatsHeld, PaymentAuthorized, BookingConfirmed {

    /**
     * Minted once, when the producing service writes its outbox row. Consumers dedup on it,
     * so a value generated per delivery instead would dedup nothing.
     */
    UUID eventId();

    /**
     * Also the Kafka message key, which is what keeps one booking's events on one partition
     * and therefore in order.
     */
    UUID bookingId();

    Instant occurredAt();

    int schemaVersion();

    /**
     * The topic this event belongs on, so the outbox row can be written without a lookup
     * table mapping types to destinations. Routing, not payload — it never goes on the wire.
     */
    @JsonIgnore
    String topic();

}
