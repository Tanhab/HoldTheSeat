package com.tanhab.holdtheseat.booking.expiry;

import com.tanhab.holdtheseat.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves stale PENDING bookings to EXPIRED. Consumes nothing — idempotency is the status
 * guard on the UPDATE, not a processed_events row.
 */
@Component
public class ExpirySweeper {

    private final BookingService bookingService;

    public ExpirySweeper(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${holdtheseat.expiry.poll-interval-ms}")
    public void sweep() {
        bookingService.expireStale();
    }

}
