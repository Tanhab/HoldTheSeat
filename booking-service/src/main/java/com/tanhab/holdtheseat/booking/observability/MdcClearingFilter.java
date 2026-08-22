package com.tanhab.holdtheseat.booking.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Clears MDC at the end of every HTTP request so a pooled thread cannot leak a previous
 * bookingId into the next request's logs. Callers put values; this only sweeps.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcClearingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        BookingIdMdc.putCorrelationId(request.getHeader(CORRELATION_HEADER));
        try {
            filterChain.doFilter(request, response);
        } finally {
            BookingIdMdc.clear();
        }
    }

}
