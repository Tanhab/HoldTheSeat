package com.tanhab.holdtheseat.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stands in for a card processor. It approves everything, which is what makes the happy path
 * demonstrable end to end without a real provider or a network.
 *
 * <p>Its own class rather than a branch inside the service, because the decline rule that
 * makes the compensation demo repeatable belongs here and nowhere else.
 */
@Component
public class MockCardGateway {

    public GatewayResult authorize(UUID bookingId, long amountCents) {
        return GatewayResult.approved("mock-" + UUID.randomUUID());
    }

}
