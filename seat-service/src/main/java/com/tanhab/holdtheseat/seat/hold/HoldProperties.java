package com.tanhab.holdtheseat.seat.hold;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds holdtheseat.hold.* from application.yml.
 *
 * @param ttl how long a hold survives without being confirmed — the payment window
 */
@ConfigurationProperties(prefix = "holdtheseat.hold")
public record HoldProperties(Duration ttl) {
}
