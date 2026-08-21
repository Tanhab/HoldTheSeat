package com.tanhab.holdtheseat.booking.timeline;

import com.tanhab.holdtheseat.booking.dto.BookingTimelineResponse;
import com.tanhab.holdtheseat.booking.dto.TimelineEntryResponse;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.SeatsRejected;
import com.tanhab.holdtheseat.events.SeatsReleased;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class BookingTimelineService {

    private final BookingTimelineRepository timelineRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    public BookingTimelineService(BookingTimelineRepository timelineRepository,
                                  BookingRepository bookingRepository,
                                  ObjectMapper objectMapper) {
        this.timelineRepository = timelineRepository;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist one diary row for an event this service produced or consumed.
     * Call inside the same {@code @Transactional} boundary as the outbox / status change.
     */
    public void append(DomainEvent event) {
        timelineRepository.append(
                event.bookingId(),
                event.eventId(),
                eventType(event),
                event.occurredAt(),
                objectMapper.writeValueAsString(event));
    }

    @Transactional(readOnly = true)
    public BookingTimelineResponse timelineFor(UUID bookingId) {
        if (bookingRepository.findById(bookingId).isEmpty()) {
            throw new BookingNotFoundException(bookingId);
        }

        List<TimelineEntryResponse> events = timelineRepository.findByBookingId(bookingId).stream()
                .map(e -> new TimelineEntryResponse(
                        e.eventId(),
                        e.eventType(),
                        e.occurredAt(),
                        e.payload()))
                .toList();

        return new BookingTimelineResponse(bookingId, events);
    }

    private static String eventType(DomainEvent event) {
        return switch (event) {
            case BookingRequested _ -> BookingRequested.TYPE;
            case BookingConfirmed _ -> BookingConfirmed.TYPE;
            case BookingCancelled _ -> BookingCancelled.TYPE;
            case BookingExpired _ -> BookingExpired.TYPE;
            case SeatsHeld _ -> SeatsHeld.TYPE;
            case SeatsRejected _ -> SeatsRejected.TYPE;
            case SeatsReleased _ -> SeatsReleased.TYPE;
            case PaymentAuthorized _ -> PaymentAuthorized.TYPE;
            case PaymentFailed _ -> PaymentFailed.TYPE;
        };
    }

}
