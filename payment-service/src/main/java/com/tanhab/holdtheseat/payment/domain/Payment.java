package com.tanhab.holdtheseat.payment.domain;

import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID bookingId,
        long amountCents,
        PaymentStatus status,
        String gatewayRef,
        Instant createdAt
) {
}
