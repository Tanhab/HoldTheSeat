package com.tanhab.holdtheseat.booking.timeline;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.dto.TimelineEntryResponse;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.SeatsReleased;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BookingTimelineConsumeTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingTimelineService timelineService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void happyPathTimelineIncludesHeldAuthorizedAndConfirmed() {
        BookingResponse pending = createBooking();
        SeatsHeld held = SeatsHeld.of(
                pending.id(), pending.showId(), pending.seatIds(), pending.customerId(),
                5000L, Instant.now().plusSeconds(600));
        PaymentAuthorized authorized = PaymentAuthorized.of(pending.id(), 5000L, "mock-ref");

        publish(held);
        publish(authorized);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(typesFor(pending.id())).containsExactlyInAnyOrder(
                        BookingRequested.TYPE,
                        SeatsHeld.TYPE,
                        PaymentAuthorized.TYPE,
                        BookingConfirmed.TYPE));
    }

    @Test
    void compensationTimelineIncludesFailedCancelledAndReleased() {
        BookingResponse pending = createBooking();
        SeatsHeld held = SeatsHeld.of(
                pending.id(), pending.showId(), pending.seatIds(), pending.customerId(),
                5000L, Instant.now().plusSeconds(600));
        PaymentFailed failed = PaymentFailed.of(pending.id(), 5000L, "card_declined");
        SeatsReleased released = SeatsReleased.of(
                pending.id(), pending.showId(), pending.seatIds(), pending.seatIds().size());

        publish(held);
        publish(failed);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(typesFor(pending.id())).contains(
                        BookingRequested.TYPE,
                        SeatsHeld.TYPE,
                        PaymentFailed.TYPE,
                        BookingCancelled.TYPE));

        publish(released);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(typesFor(pending.id())).contains(SeatsReleased.TYPE));
    }

    @Test
    void redeliveredSeatsHeldDoesNotDuplicateTimeline() {
        BookingResponse pending = createBooking();
        SeatsHeld held = SeatsHeld.of(
                pending.id(), pending.showId(), pending.seatIds(), pending.customerId(),
                5000L, Instant.now().plusSeconds(600));

        publish(held);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(typesFor(pending.id())).contains(SeatsHeld.TYPE));

        publish(held);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(timelineService.timelineFor(pending.id()).events().stream()
                        .filter(e -> e.eventType().equals(SeatsHeld.TYPE))
                        .map(TimelineEntryResponse::eventId)
                        .toList()).containsExactly(held.eventId()));
    }

    private BookingResponse createBooking() {
        return bookingService.create(new CreateBookingRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), "cust-timeline"));
    }

    private void publish(DomainEvent event) {
        kafkaTemplate.send(event.topic(), event.bookingId().toString(),
                objectMapper.writeValueAsString(event)).join();
    }

    private List<String> typesFor(UUID bookingId) {
        return timelineService.timelineFor(bookingId).events().stream()
                .map(TimelineEntryResponse::eventType)
                .toList();
    }

}
