package com.tanhab.holdtheseat.booking.service;

import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;

    public BookingService(final BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public BookingResponse create(CreateBookingRequest request) {
        Booking created = bookingRepository.insert(request.showId(), request.seatIds(), request.customerId(),
                request.amountCents());
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
