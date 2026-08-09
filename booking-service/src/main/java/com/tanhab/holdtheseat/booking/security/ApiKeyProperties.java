package com.tanhab.holdtheseat.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds holdtheseat.api-key.* from application.yml.
 *
 * @param hash hex-encoded SHA-256 of the accepted key. The key itself is never stored.
 */
@ConfigurationProperties(prefix = "holdtheseat.api-key")
public record ApiKeyProperties(String hash) {
}
