package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.seat.hold.SeatHoldStore;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SeatSettlementService {

    private static final Logger log = LoggerFactory.getLogger(SeatSettlementService.class);

    private final SeatRepository seatRepository;
    private final SeatHoldStore holdStore;

    public SeatSettlementService(SeatRepository seatRepository, SeatHoldStore holdStore) {
        this.seatRepository = seatRepository;
        this.holdStore = holdStore;
    }

    /**
     * Validate, then write Postgres, then bring Redis into line.
     *
     * <p>That order is the design. Writing Redis first would let a crash leave the seats sold
     * in Redis and available in Postgres, and the startup warm-up rebuilds the sold set from
     * Postgres — so it would resurrect a paid seat as available. The other way round, a crash
     * between the two leaves Postgres SOLD with a stale hold key that lapses on its own, and
     * the same warm-up repairs the sold set. Both orders have a window; only one of them can
     * sell a seat twice.
     */
    public void settle(BookingConfirmed confirmed) {
        if (!holdStore.holdsAreIntact(confirmed.showId(), confirmed.bookingId(), confirmed.seatIds())) {
            // Hard problem #6's minimum bar. The payment landed after the hold lapsed, so the
            // seats are gone or someone else's. Refusing here is what stops a phantom sale;
            // refunding the customer is a compensation this phase does not build.
            log.error("Booking {} confirmed but its hold is gone — seats not sold", confirmed.bookingId());
            return;
        }

        int sold = seatRepository.markSold(confirmed.showId(), confirmed.seatIds());
        if (sold != confirmed.seatIds().size()) {
            // Unreachable unless something sold a seat this booking still held, which the
            // claim script forbids. Loud rather than partial: the transaction rolls back.
            throw new IllegalStateException(
                    "Booking %s held %d seats but only %d moved to SOLD"
                            .formatted(confirmed.bookingId(), confirmed.seatIds().size(), sold));
        }

        holdStore.settle(confirmed.showId(), confirmed.bookingId(), confirmed.seatIds());
        log.info("Sold {} seats for booking {}", sold, confirmed.bookingId());
    }

}
