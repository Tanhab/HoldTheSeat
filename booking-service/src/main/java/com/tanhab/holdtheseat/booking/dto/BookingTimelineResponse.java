package com.tanhab.holdtheseat.booking.dto;

import java.util.List;
import java.util.UUID;

public record BookingTimelineResponse(
        UUID bookingId,
        List<TimelineEntryResponse> events
) {
}
