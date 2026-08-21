package com.tanhab.holdtheseat.booking.timeline;

import java.time.Instant;
import java.util.UUID;

public record BookingTimelineEntry(
        UUID id,
        UUID bookingId,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String payload,
        Instant recordedAt
) {
}
