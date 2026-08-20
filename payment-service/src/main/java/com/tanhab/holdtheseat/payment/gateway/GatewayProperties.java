package com.tanhab.holdtheseat.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which customer the mock gateway declines, so the compensation demo is repeatable. Bound the
 * same way as the seat service's hold TTL — a config value, not a constant, so a test can point
 * it at whatever id it likes.
 *
 * @param declineCustomerId the customer id that always fails authorization
 */
@ConfigurationProperties(prefix = "holdtheseat.gateway")
public record GatewayProperties(String declineCustomerId) {
}
