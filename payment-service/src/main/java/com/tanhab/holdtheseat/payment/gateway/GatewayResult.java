package com.tanhab.holdtheseat.payment.gateway;

/**
 * @param reference     the gateway's handle on the charge, and the only thing a refund could be
 *                      issued against; null on a decline
 * @param declineReason the processor's own reason code, passed through untouched; null on approval
 */
public record GatewayResult(boolean approved, String reference, String declineReason) {

    public static GatewayResult approved(String reference) {
        return new GatewayResult(true, reference, null);
    }

    public static GatewayResult declined(String declineReason) {
        return new GatewayResult(false, null, declineReason);
    }

}
