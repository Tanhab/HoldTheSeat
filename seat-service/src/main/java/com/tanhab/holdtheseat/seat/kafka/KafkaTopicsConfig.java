package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Only the topic this service produces to. The {@code bookings} topic it consumes is
 * declared by the service that writes to it, so ownership of a topic stays with one service.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic seatsTopic() {
        return TopicBuilder.name(Topics.SEATS)
                .partitions(Topics.PARTITIONS)
                .replicas(1)
                .build();
    }

}
