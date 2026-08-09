package com.tanhab.holdtheseat.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Identifies the caller from the X-API-Key header. It never rejects a request — the
 * authorization rules decide what an unidentified caller is allowed to reach.
 *
 * <p>Not a {@code @Component} on purpose: Spring Boot auto-registers any {@code Filter}
 * bean with the servlet container, which would run it outside the security chain as well.
 * The security configuration constructs it instead.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String PRINCIPAL = "api-client";
    private static final int SHA_256_BYTES = 32;

    private final byte[] expectedKeyHash;

    public ApiKeyAuthFilter(ApiKeyProperties properties) {
        this.expectedKeyHash = HexFormat.of().parseHex(properties.hash());
        if (expectedKeyHash.length != SHA_256_BYTES) {
            throw new IllegalArgumentException(
                    "holdtheseat.api-key.hash must be a hex-encoded SHA-256 digest (64 hex characters)");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String presentedKey = request.getHeader(API_KEY_HEADER);
        if (presentedKey != null && matchesExpectedKey(presentedKey)) {
            authenticateApiClient();
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesExpectedKey(String presentedKey) {
        return MessageDigest.isEqual(expectedKeyHash, sha256(presentedKey));
    }

    private static void authenticateApiClient() {
        // Three-argument constructor: marks the token authenticated. The two-argument one
        // does not, and the request would be denied despite a valid key.
        var authentication = new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private static byte[] sha256(String value) {
        try {
            // MessageDigest is stateful and not thread-safe, so never cache the instance.
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM is required to provide SHA-256", e);
        }
    }

}
