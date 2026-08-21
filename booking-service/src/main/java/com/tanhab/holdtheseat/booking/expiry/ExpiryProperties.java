package com.tanhab.holdtheseat.booking.expiry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Must stay strictly longer than the seat service's {@code holdtheseat.hold.ttl}. Nothing
 * enforces that across the two yamls — invert them and a slow payment authorises after the
 * booking is already EXPIRED while the hold is still live.
 *
 * @param after           age since {@code created_at} before a PENDING booking is expired
 * @param pollIntervalMs  how often the sweeper looks for stale rows
 * @param batchSize       rows claimed per pass
 */
@ConfigurationProperties(prefix = "holdtheseat.expiry")
public record ExpiryProperties(Duration after, long pollIntervalMs, int batchSize) {
}
