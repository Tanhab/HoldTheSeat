package com.tanhab.holdtheseat.seat.service;

import com.tanhab.holdtheseat.events.SeatsReleased;
import com.tanhab.holdtheseat.seat.hold.SeatHoldStore;
import com.tanhab.holdtheseat.seat.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * The compensating transaction: give a booking's seats back. The mirror of
 * {@link SeatSettlementService}, and deliberately smaller — a held seat was never durably
 * anything, so putting it back touches only Redis, the one store that ever knew about it.
 *
 * <p>Release is idempotent by construction (see {@code release-hold.lua}): it frees only the
 * holds still owned by this booking and returns how many that was. A rejection that never held
 * a seat, a cancellation or expiry arriving after the TTL already lapsed, and a redelivery
 * all free zero.
 */
@Service
public class SeatReleaseService {

    private static final Logger log = LoggerFactory.getLogger(SeatReleaseService.class);

    private final SeatHoldStore holdStore;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SeatReleaseService(SeatHoldStore holdStore,
                              OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.holdStore = holdStore;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Release whatever this booking still holds, and announce it only when something was
     * actually freed — so a {@link SeatsReleased} on the wire always means work was undone,
     * never merely that a cancellation or expiry was seen.
     */
    public void release(UUID bookingId, UUID showId, List<UUID> seatIds) {
        long freed = holdStore.release(bookingId);
        if (freed == 0) {
            return;
        }

        SeatsReleased released = SeatsReleased.of(bookingId, showId, seatIds, freed);
        outboxRepository.append(released.bookingId(), SeatsReleased.TYPE, released.topic(),
                objectMapper.writeValueAsString(released));
        log.info("Released {} seats for booking {}", freed, bookingId);
    }

}
