package com.tanhab.holdtheseat.seat.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Replaces the default retry policy, which gives up after ten immediate attempts and then
 * commits the offset anyway — quietly dropping the record. A dropped event strands its saga
 * with no trace, which is the failure the outbox exists to prevent, so it must not be
 * reintroduced at the consuming end.
 *
 * <p>This handler never gives up. A record that keeps failing holds its partition and keeps
 * logging, which is loud and blocks that partition's other bookings — the honest behaviour
 * when the alternative is silence. The container pauses the consumer between attempts, so
 * backing off does not look like a dead member and trigger a rebalance.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);

        return new DefaultErrorHandler(backOff);
    }

}
