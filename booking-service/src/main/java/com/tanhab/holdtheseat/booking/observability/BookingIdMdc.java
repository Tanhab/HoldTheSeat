package com.tanhab.holdtheseat.booking.observability;

import com.tanhab.holdtheseat.events.DomainEvent;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Puts the saga's greppable id into SLF4J MDC. bookingId is already on every event and is the
 * Kafka key — this only makes logs joinable across services.
 */
public final class BookingIdMdc {

    public static final String BOOKING_ID = "bookingId";
    public static final String EVENT_TYPE = "eventType";
    public static final String CORRELATION_ID = "correlationId";

    private BookingIdMdc() {
    }

    public static void put(DomainEvent event) {
        MDC.put(BOOKING_ID, event.bookingId().toString());
        MDC.put(EVENT_TYPE, event.getClass().getSimpleName());
    }

    public static void putBookingId(UUID bookingId) {
        MDC.put(BOOKING_ID, bookingId.toString());
    }

    public static void putCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID, correlationId.trim());
        }
    }

    public static void clear() {
        MDC.remove(BOOKING_ID);
        MDC.remove(EVENT_TYPE);
        MDC.remove(CORRELATION_ID);
    }

}
