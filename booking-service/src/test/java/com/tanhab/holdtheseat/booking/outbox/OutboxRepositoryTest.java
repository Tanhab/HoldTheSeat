package com.tanhab.holdtheseat.booking.outbox;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearOutbox() {
        jdbcClient.sql("DELETE FROM outbox").update();
    }

    @Test
    void appendedRowComesBackWithItsPayloadIntact() {
        UUID bookingId = UUID.randomUUID();
        String payload = """
                {"eventType":"BookingRequested","bookingId":"%s"}""".formatted(bookingId);

        outboxRepository.append(bookingId, BookingRequested.TYPE, Topics.BOOKINGS, payload);

        List<OutboxRecord> claimed = outboxRepository.claimUnpublished(10);

        assertThat(claimed).singleElement().satisfies(record -> {
            assertThat(record.id()).isNotNull();
            assertThat(record.aggregateId()).isEqualTo(bookingId);
            assertThat(record.topic()).isEqualTo(Topics.BOOKINGS);
            assertThat(compact(record.payload())).contains("\"bookingId\":\"%s\"".formatted(bookingId));
        });
    }

    @Test
    void claimsOldestFirstAndHonoursTheLimit() {
        append("one");
        append("two");
        append("three");

        List<OutboxRecord> claimed = outboxRepository.claimUnpublished(2);

        assertThat(claimed).hasSize(2);
        assertThat(claimed).extracting(record -> compact(record.payload()))
                .containsExactly(payloadFor("one"), payloadFor("two"));
    }

    @Test
    void publishedRowsAreNeverClaimedAgain() {
        append("one");
        append("two");

        List<OutboxRecord> firstPass = outboxRepository.claimUnpublished(10);
        outboxRepository.markPublished(firstPass.stream().map(OutboxRecord::id).toList());

        assertThat(outboxRepository.claimUnpublished(10)).isEmpty();
    }

    @Test
    void markPublishedToleratesAnEmptyBatch() {
        outboxRepository.markPublished(List.of());

        assertThat(outboxRepository.claimUnpublished(10)).isEmpty();
    }

    /**
     * The property the whole poller design rests on: a second poller must step over a claimed
     * batch rather than block behind it. The second claim runs on another thread so it gets
     * its own connection, outside the transaction holding the lock.
     */
    @Test
    void aConcurrentPollerSkipsRowsThisOneHasClaimed() {
        append("one");
        append("two");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<OutboxRecord> mine = outboxRepository.claimUnpublished(10);
            assertThat(mine).hasSize(2);

            List<OutboxRecord> theirs = CompletableFuture
                    .supplyAsync(() -> outboxRepository.claimUnpublished(10))
                    .join();

            assertThat(theirs).isEmpty();
        });
    }

    @Test
    void aConcurrentPollerTakesTheRowsThisOneLeftBehind() {
        append("one");
        append("two");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outboxRepository.claimUnpublished(1);

            List<OutboxRecord> theirs = CompletableFuture
                    .supplyAsync(() -> outboxRepository.claimUnpublished(10))
                    .join();

            assertThat(theirs).extracting(record -> compact(record.payload()))
                    .containsExactly(payloadFor("two"));
        });
    }

    private void append(String marker) {
        outboxRepository.append(UUID.randomUUID(), BookingRequested.TYPE, Topics.BOOKINGS, payloadFor(marker));
    }

    private static String payloadFor(String marker) {
        return "{\"marker\":\"%s\"}".formatted(marker);
    }

    /**
     * jsonb round-trips the meaning, not the bytes: it re-renders from its parsed form with
     * normalised whitespace. Comparing the compacted text keeps these assertions about
     * content rather than about Postgres's formatting.
     */
    private static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }

}
