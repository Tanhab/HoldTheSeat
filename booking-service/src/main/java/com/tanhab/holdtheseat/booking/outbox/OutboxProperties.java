package com.tanhab.holdtheseat.booking.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param pollIntervalMs how often the poller looks for unpublished rows
 * @param batchSize      rows claimed per pass; caps how much one slow send can hold up
 */
@ConfigurationProperties(prefix = "holdtheseat.outbox")
public record OutboxProperties(long pollIntervalMs, int batchSize) {
}
