package com.tanhab.holdtheseat.booking.service;

import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.outbox.OutboxRepository;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BookingService(BookingRepository bookingRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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
    }

    public BookingResponse findById(UUID id) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        return toBookingResponse(booking);
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return new BookingResponse(booking.id(), booking.showId(), booking.seatIds(), booking.customerId(),
                booking.amountCents(), booking.status(), booking.createdAt());
    }

}
