package com.tanhab.holdtheseat.booking.controller;

import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.BookingTimelineResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.booking.timeline.BookingTimelineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController {
    private final BookingService bookingService;
    private final BookingTimelineService timelineService;

    public BookingController(BookingService bookingService, BookingTimelineService timelineService) {
        this.bookingService = bookingService;
        this.timelineService = timelineService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse created = bookingService.create(request);
        UriComponents uriComponents = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(uriComponents.toUri()).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable UUID id) {
        BookingResponse found = bookingService.findById(id);
        return new ResponseEntity<>(found, HttpStatus.OK);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<BookingTimelineResponse> getTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(timelineService.timelineFor(id));
    }
}
