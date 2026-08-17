package com.tanhab.holdtheseat.seat.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, String> kafkaTemplate,
                        OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Transactional across a network call, which is normally a smell and here is the whole
     * mechanism: {@code FOR UPDATE SKIP LOCKED} holds its locks only until the transaction
     * ends, so committing before the sends complete would free the rows for a second poller
     * to claim and publish again.
     *
     * <p>A failure after some sends succeeded rolls the batch back unpublished and the next
     * pass resends all of it. Duplicates on the topic are the accepted cost — losing an event
     * is not recoverable, and every consumer dedups.
     */
    @Scheduled(fixedDelayString = "${holdtheseat.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxRecord> batch = outboxRepository.claimUnpublished(properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }

        List<CompletableFuture<SendResult<String, String>>> sends = batch.stream()
                // The key is the booking id: it routes every event for one booking to one
                // partition, which is the only reason their order survives.
                .map(record -> kafkaTemplate.send(record.topic(), record.aggregateId().toString(), record.payload()))
                .toList();

        // Sending is asynchronous. Stamping published_at before the broker has acknowledged
        // would mark a row done that never left the process, and nothing would retry it.
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();

        outboxRepository.markPublished(batch.stream().map(OutboxRecord::id).toList());
        log.debug("Published {} outbox records", batch.size());
    }

}
