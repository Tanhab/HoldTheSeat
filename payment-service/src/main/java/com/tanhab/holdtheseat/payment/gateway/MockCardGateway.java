package com.tanhab.holdtheseat.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Stands in for a card processor. It approves everything except one configured customer, whose
 * charges always decline — which is what makes the compensation path demonstrable end to end
 * and repeatable, without a real provider or a network.
 *
 * <p>Its own class rather than a branch inside the service, because the decline rule belongs
 * here and nowhere else.
 */
@Component
public class MockCardGateway {

    private final GatewayProperties properties;

    public MockCardGateway(GatewayProperties properties) {
        this.properties = properties;
    }

    public GatewayResult authorize(UUID bookingId, String customerId, long amountCents) {
        if (Objects.equals(properties.declineCustomerId(), customerId)) {
            return GatewayResult.declined("card_declined");
        }
        return GatewayResult.approved("mock-" + UUID.randomUUID());
    }

}
