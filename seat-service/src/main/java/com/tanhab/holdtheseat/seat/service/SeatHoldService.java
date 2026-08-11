package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.exception.UnknownSeatException;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import com.tanhab.holdtheseat.seat.hold.SeatHoldStore;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import com.tanhab.holdtheseat.seat.repository.ShowRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SeatHoldService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldStore holdStore;

    public SeatHoldService(ShowRepository showRepository,
                           SeatRepository seatRepository,
                           SeatHoldStore holdStore) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.holdStore = holdStore;
    }

    /**
     * Seat ids arrive from an untrusted caller, so they are checked against Postgres before
     * Redis sees them. A seat that does not belong to this show is a bad request, not a
     * conflict — conflating the two would report "someone else has it" for an id that never
     * existed.
     */
    public HoldOutcome hold(UUID showId, UUID bookingId, List<UUID> seatIds) {
        if (showRepository.findById(showId).isEmpty()) {
            throw new ShowNotFoundException(showId);
        }

        List<UUID> requested = List.copyOf(new LinkedHashSet<>(seatIds));
        Set<UUID> known = seatRepository.findByIds(showId, requested).stream()
                .map(Seat::id)
                .collect(java.util.stream.Collectors.toSet());

        List<UUID> unknown = requested.stream().filter(id -> !known.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new UnknownSeatException(showId, unknown);
        }

        return holdStore.hold(showId, bookingId, requested);
    }

}
