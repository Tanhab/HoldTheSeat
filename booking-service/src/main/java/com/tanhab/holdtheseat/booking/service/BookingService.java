package com.tanhab.holdtheseat.booking.service;

import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.outbox.OutboxRepository;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import com.tanhab.holdtheseat.events.BookingRequested;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class BookingService {

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

    public BookingResponse findById(UUID id) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        return toBookingResponse(booking);
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return new BookingResponse(booking.id(), booking.showId(), booking.seatIds(), booking.customerId(),
                booking.amountCents(), booking.status(), booking.createdAt());
    }

}
