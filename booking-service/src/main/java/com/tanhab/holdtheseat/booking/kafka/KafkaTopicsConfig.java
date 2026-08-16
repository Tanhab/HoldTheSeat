package com.tanhab.holdtheseat.booking.kafka;

import com.tanhab.holdtheseat.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Compose disables auto-creation, so the service that produces to a topic declares it.
 * {@code KafkaAdmin} creates what is missing at startup and never removes or shrinks
 * anything, so this is safe to run against an existing broker.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic bookingsTopic() {
        return TopicBuilder.name(Topics.BOOKINGS)
                .partitions(Topics.PARTITIONS)
                .replicas(1)
                .build();
    }

}
