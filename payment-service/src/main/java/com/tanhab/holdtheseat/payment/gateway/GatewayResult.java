package com.tanhab.holdtheseat.payment.gateway;

/**
 * @param reference the gateway's handle on the charge, and the only thing a refund could be
 *                  issued against
 */
public record GatewayResult(boolean approved, String reference) {

    public static GatewayResult approved(String reference) {
        return new GatewayResult(true, reference);
    }

}
