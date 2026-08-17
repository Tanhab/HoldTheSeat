package com.tanhab.holdtheseat.seat.hold;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the hold scripts against Redis. All the decision logic lives in the Lua; this only
 * marshals arguments and reads the reply.
 */
@Component
public class SeatHoldStore {

    private final StringRedisTemplate redis;
    private final HoldProperties properties;
    private final RedisScript<List> holdScript;
    private final RedisScript<Long> releaseScript;
    private final RedisScript<Long> validateScript;
    private final RedisScript<Long> settleScript;

    public SeatHoldStore(StringRedisTemplate redis, HoldProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.holdScript = load("redis/hold-seats.lua", List.class);
        this.releaseScript = load("redis/release-hold.lua", Long.class);
        this.validateScript = load("redis/validate-hold.lua", Long.class);
        this.settleScript = load("redis/settle-hold.lua", Long.class);
    }

    public HoldOutcome hold(UUID showId, UUID bookingId, List<UUID> seatIds) {
        List<String> keys = List.of(HoldKeys.bookingHolds(bookingId));

        List<String> args = new ArrayList<>();
        args.add(bookingId.toString());
        args.add(String.valueOf(properties.ttl().toMillis()));
        args.add(HoldKeys.sold(showId));
        for (UUID seatId : seatIds) {
            args.add(HoldKeys.hold(showId, seatId));
            args.add(seatId.toString());
        }

        List<?> reply = redis.execute(holdScript, keys, args.toArray());
        return toOutcome(reply);
    }

    private static HoldOutcome toOutcome(List<?> reply) {
        if (reply == null || reply.isEmpty()) {
            throw new IllegalStateException("hold-seats.lua returned no verdict");
        }
        if (reply.getFirst() instanceof Number verdict && verdict.longValue() == 1L) {
            return HoldOutcome.allSeatsHeld();
        }
        return HoldOutcome.blockedBy(reply.stream()
                .skip(1)
                .map(Object::toString)
                .map(UUID::fromString)
                .toList());
    }

    /**
     * @return how many holds were actually released, which is 0 when they had already
     * expired or been released
     */
    public long release(UUID bookingId) {
        Long released = redis.execute(releaseScript,
                List.of(HoldKeys.bookingHolds(bookingId)),
                bookingId.toString());

        return released == null ? 0L : released;
    }

    /**
     * @return whether every seat is still held by this booking, so the sale can go ahead
     */
    public boolean holdsAreIntact(UUID showId, UUID bookingId, List<UUID> seatIds) {
        List<String> args = new ArrayList<>();
        args.add(bookingId.toString());
        for (UUID seatId : seatIds) {
            args.add(HoldKeys.hold(showId, seatId));
        }

        Long intact = redis.execute(validateScript,
                List.of(HoldKeys.bookingHolds(bookingId)),
                args.toArray());

        return intact != null && intact == 1L;
    }

    /**
     * Moves the seats into the sold set and drops the holds. Called only after Postgres has
     * committed them as SOLD.
     *
     * @return how many hold keys were deleted
     */
    public long settle(UUID showId, UUID bookingId, List<UUID> seatIds) {
        List<String> args = new ArrayList<>();
        args.add(HoldKeys.sold(showId));
        for (UUID seatId : seatIds) {
            args.add(HoldKeys.hold(showId, seatId));
            args.add(seatId.toString());
        }

        Long settled = redis.execute(settleScript,
                List.of(HoldKeys.bookingHolds(bookingId)),
                args.toArray());

        return settled == null ? 0L : settled;
    }

    /**
     * {@link DefaultRedisScript} caches the SHA and sends EVALSHA, falling back to the full
     * body when the server answers NOSCRIPT — the script cache does not survive a restart.
     */
    private static <T> RedisScript<T> load(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(resultType);
        return script;
    }

}
