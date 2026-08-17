package com.tanhab.holdtheseat.payment.kafka;

import com.tanhab.holdtheseat.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic paymentsTopic() {
        return TopicBuilder.name(Topics.PAYMENTS)
                .partitions(Topics.PARTITIONS)
                .replicas(1)
                .build();
    }

}
