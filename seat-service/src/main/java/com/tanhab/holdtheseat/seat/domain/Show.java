package com.tanhab.holdtheseat.seat.domain;

import java.time.Instant;
import java.util.UUID;

public record Show(
        UUID id,
        String name,
        Instant startsAt,
        Instant createdAt
) {
}
