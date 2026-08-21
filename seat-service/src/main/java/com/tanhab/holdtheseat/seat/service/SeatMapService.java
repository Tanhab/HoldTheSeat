package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.domain.SeatMapStatus;
import com.tanhab.holdtheseat.seat.domain.SeatStatus;
import com.tanhab.holdtheseat.seat.dto.SeatMapEntry;
import com.tanhab.holdtheseat.seat.dto.SeatMapResponse;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.hold.SeatHoldStore;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import com.tanhab.holdtheseat.seat.repository.ShowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SeatMapService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldStore holdStore;

    public SeatMapService(ShowRepository showRepository,
                          SeatRepository seatRepository,
                          SeatHoldStore holdStore) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.holdStore = holdStore;
    }

    /**
     * Postgres is durable AVAILABLE/SOLD; HELD is inferred from Redis hold keys. Sold wins
     * over a stray hold key — durable state is the source of truth for a completed sale.
     */
    public SeatMapResponse mapForShow(UUID showId) {
        if (showRepository.findById(showId).isEmpty()) {
            throw new ShowNotFoundException(showId);
        }

        List<Seat> seats = seatRepository.findByShowId(showId);
        Set<UUID> held = holdStore.heldAmong(showId, seats.stream().map(Seat::id).toList());

        List<SeatMapEntry> entries = seats.stream()
                .map(seat -> new SeatMapEntry(
                        seat.id(),
                        seat.seatRow(),
                        seat.seatNumber(),
                        seat.priceCents(),
                        mapStatus(seat, held)))
                .toList();

        return new SeatMapResponse(showId, entries);
    }

    private static SeatMapStatus mapStatus(Seat seat, Set<UUID> held) {
        if (seat.status() == SeatStatus.SOLD) {
            return SeatMapStatus.SOLD;
        }
        if (held.contains(seat.id())) {
            return SeatMapStatus.HELD;
        }
        return SeatMapStatus.AVAILABLE;
    }

}
