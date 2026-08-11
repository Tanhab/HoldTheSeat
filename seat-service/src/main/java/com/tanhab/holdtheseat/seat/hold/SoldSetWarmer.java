package com.tanhab.holdtheseat.seat.hold;

import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import com.tanhab.holdtheseat.seat.repository.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rebuilds {@code sold:{showId}} from Postgres.
 *
 * <p>Redis runs without persistence, so a restart returns it empty. Losing live holds is
 * survivable — they were expiring anyway — but losing the sold set is not: the claim script
 * would see no hold and no sale, and grant a seat that has already been paid for. Postgres
 * is the source of truth for SOLD; this set is a cache of that one fact.
 */
@Component
public class SoldSetWarmer {

    private static final Logger log = LoggerFactory.getLogger(SoldSetWarmer.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final StringRedisTemplate redis;

    public SoldSetWarmer(ShowRepository showRepository,
                         SeatRepository seatRepository,
                         StringRedisTemplate redis) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.redis = redis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmSoldSets() {
        int totalSeats = 0;
        List<UUID> showIds = showRepository.findAllIds();

        for (UUID showId : showIds) {
            totalSeats += warm(showId);
        }

        log.info("Warmed sold seat sets for {} shows, {} sold seats total", showIds.size(), totalSeats);
    }

    private int warm(UUID showId) {
        List<UUID> soldSeatIds = seatRepository.findSoldSeatIds(showId);
        String key = HoldKeys.sold(showId);

        // Replace rather than top up: the set must end up matching Postgres exactly, and
        // SADD alone would preserve stale members from a previous run.
        redis.delete(key);
        if (soldSeatIds.isEmpty()) {
            // SADD with no members is an error, and a show with nothing sold is the normal
            // case on a fresh database.
            return 0;
        }

        redis.opsForSet().add(key, soldSeatIds.stream().map(UUID::toString).toArray(String[]::new));
        return soldSeatIds.size();
    }

}
