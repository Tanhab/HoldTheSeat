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

    public SeatHoldStore(StringRedisTemplate redis, HoldProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.holdScript = load("redis/hold-seats.lua");
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
     * {@link DefaultRedisScript} caches the SHA and sends EVALSHA, falling back to the full
     * body when the server answers NOSCRIPT — the script cache does not survive a restart.
     */
    private static RedisScript<List> load(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(List.class);
        return script;
    }

}
