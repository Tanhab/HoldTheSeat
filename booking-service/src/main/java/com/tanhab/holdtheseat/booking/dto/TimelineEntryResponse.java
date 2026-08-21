package com.tanhab.holdtheseat.booking.dto;

import java.time.Instant;
import java.util.UUID;

public record TimelineEntryResponse(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String payload
) {
}
