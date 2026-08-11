package com.tanhab.holdtheseat.seat.controller;

import com.tanhab.holdtheseat.seat.dto.HoldResponse;
import com.tanhab.holdtheseat.seat.dto.HoldSeatsRequest;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import com.tanhab.holdtheseat.seat.hold.HoldProperties;
import com.tanhab.holdtheseat.seat.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/holds")
public class HoldController {

    private final SeatHoldService seatHoldService;
    private final HoldProperties holdProperties;

    public HoldController(SeatHoldService seatHoldService, HoldProperties holdProperties) {
        this.seatHoldService = seatHoldService;
        this.holdProperties = holdProperties;
    }

    /**
     * A refused hold is a normal outcome rather than an error, so it is answered here
     * instead of being thrown at the exception handler. Losing a race for a seat says
     * nothing about whether the request was well formed.
     */
    @PostMapping
    public ResponseEntity<?> hold(@Valid @RequestBody HoldSeatsRequest request) {
        HoldOutcome outcome = seatHoldService.hold(request.showId(), request.bookingId(), request.seatIds());

        if (!outcome.granted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict(outcome));
        }

        HoldResponse held = new HoldResponse(
                request.bookingId(),
                request.showId(),
                request.seatIds(),
                Instant.now().plus(holdProperties.ttl()));

        // No Location header: a hold has no representation to GET. It is a lease, not a
        // resource, and it may be gone before the caller could follow the link.
        return ResponseEntity.status(HttpStatus.CREATED).body(held);
    }

    /**
     * Always 204, including when nothing was released. A cancellation that arrives twice has
     * still achieved what the caller asked for, and answering 404 would make a correct retry
     * look like a failure.
     */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> release(@PathVariable UUID bookingId) {
        seatHoldService.release(bookingId);
        return ResponseEntity.noContent().build();
    }

    private static ProblemDetail conflict(HoldOutcome outcome) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "One or more seats are no longer available");
        problem.setTitle("Seats Unavailable");
        problem.setProperty("conflictingSeatIds", outcome.conflictingSeatIds());
        return problem;
    }

}
