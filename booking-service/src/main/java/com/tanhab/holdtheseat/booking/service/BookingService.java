package com.tanhab.holdtheseat.booking.service;

import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.expiry.ExpiryProperties;
import com.tanhab.holdtheseat.booking.outbox.OutboxRepository;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.booking.timeline.BookingTimelineService;
import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.CancellationReason;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final OutboxRepository outboxRepository;
    private final ExpiryProperties expiryProperties;
    private final ObjectMapper objectMapper;
    private final BookingTimelineService bookingTimelineService;

    public BookingService(BookingRepository bookingRepository,
                          OutboxRepository outboxRepository,
                          ExpiryProperties expiryProperties,
                          ObjectMapper objectMapper,
                          BookingTimelineService bookingTimelineService) {
        this.bookingRepository = bookingRepository;
        this.outboxRepository = outboxRepository;
        this.expiryProperties = expiryProperties;
        this.objectMapper = objectMapper;
        this.bookingTimelineService = bookingTimelineService;
    }

    /**
     * The booking row and the event announcing it are written in one transaction. Publishing
     * directly to Kafka instead would leave a window where the process can die having
     * committed a booking nobody will ever hear about — no retry is possible, because the
     * only record of the intent went with it.
     */
    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        Booking created = bookingRepository.insert(request.showId(), request.seatIds(), request.customerId());

        BookingRequested event = BookingRequested.of(
                created.id(), created.showId(), created.seatIds(), created.customerId());
        outboxRepository.append(created.id(), BookingRequested.TYPE, event.topic(),
                objectMapper.writeValueAsString(event));
        bookingTimelineService.append(event);

        return toBookingResponse(created);
    }

    /**
     * Records the price the seat service quoted and announces the booking as final.
     *
     * <p>The {@code PENDING} guard inside {@code confirm} is the second defence, behind the
     * dedup row: dedup stops this event running twice, the guard stops a different event
     * confirming a booking that is already past PENDING. A booking that moves nothing is not
     * an error — it is a race that was already settled.
     */
    @Transactional
    public void confirm(PaymentAuthorized authorized) {
        if (bookingRepository.confirm(authorized.bookingId(), authorized.amountCents()) == 0) {
            log.warn("No pending booking {} to confirm", authorized.bookingId());
            return;
        }

        Booking confirmed = bookingRepository.findById(authorized.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(authorized.bookingId()));

        BookingConfirmed event = BookingConfirmed.of(
                confirmed.id(), confirmed.showId(), confirmed.seatIds());
        outboxRepository.append(confirmed.id(), BookingConfirmed.TYPE, event.topic(),
                objectMapper.writeValueAsString(event));
        bookingTimelineService.append(event);

    }

    /**
     * Cancels a PENDING booking and announces it. The seats come from the booking row rather
     * than the caller, because {@code PaymentFailed} does not carry them and the row is the
     * authority on what this booking is for — the same reason {@code confirm} re-reads them.
     */
    @Transactional
    public void cancel(UUID bookingId, CancellationReason reason) {
        if (bookingRepository.cancel(bookingId, reason) == 0) {
            log.warn("No pending booking {} to cancel", bookingId);
            return;
        }

        Booking cancelled = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        BookingCancelled event = BookingCancelled.of(
                cancelled.id(), cancelled.showId(), cancelled.seatIds(), reason);
        outboxRepository.append(event.bookingId(), BookingCancelled.TYPE, event.topic(),
                objectMapper.writeValueAsString(event));
        bookingTimelineService.append(event);
    }

    /**
     * Expires every stale PENDING booking this pass can claim, appending one
     * {@code BookingExpired} per row in the same transaction. An empty pass is normal —
     * return 0 and stay quiet at INFO.
     *
     * @return rows moved this pass
     */
    @Transactional
    public int expireStale() {
        List<Booking> expired = bookingRepository.expireStale(
                expiryProperties.after(), expiryProperties.batchSize());
        if (expired.isEmpty()) {
            return 0;
        }
        for (Booking booking : expired) {
            BookingExpired event = BookingExpired.of(
                    booking.id(), booking.showId(), booking.seatIds());
            outboxRepository.append(event.bookingId(), BookingExpired.TYPE, event.topic(),
                    objectMapper.writeValueAsString(event));
            bookingTimelineService.append(event);
        }
        log.debug("Expired {} stale bookings", expired.size());

        return expired.size();
    }


    public BookingResponse findById(UUID id) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        return toBookingResponse(booking);
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return new BookingResponse(booking.id(), booking.showId(), booking.seatIds(), booking.customerId(),
                booking.amountCents(), booking.status(), booking.cancellationReason(), booking.createdAt());
    }

}
