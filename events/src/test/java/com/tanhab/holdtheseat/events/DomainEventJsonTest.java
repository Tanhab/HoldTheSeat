package com.tanhab.holdtheseat.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventJsonTest {

    // A bare mapper on purpose: no Spring, no auto-configuration. If the contract only holds
    // because Boot configured something, it is not a contract.
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHOW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<UUID> SEAT_IDS = List.of(
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"));

    static Stream<DomainEvent> everyEventType() {
        return Stream.of(
                BookingRequested.of(BOOKING_ID, SHOW_ID, SEAT_IDS, "cust-42"),
                SeatsHeld.of(BOOKING_ID, SHOW_ID, SEAT_IDS, 9000L, Instant.now().plusSeconds(600)),
                PaymentAuthorized.of(BOOKING_ID, 9000L, "mock-ref-1"),
                BookingConfirmed.of(BOOKING_ID, SHOW_ID, SEAT_IDS));
    }

    @ParameterizedTest
    @MethodSource("everyEventType")
    void survivesARoundTripThroughTheInterface(DomainEvent event) {
        String json = mapper.writeValueAsString(event);

        // Reading as DomainEvent, not as the concrete type, is the whole point: a consumer
        // knows the topic, never which of the types on it just arrived.
        DomainEvent restored = mapper.readValue(json, DomainEvent.class);

        assertThat(restored).isEqualTo(event).isExactlyInstanceOf(event.getClass());
    }

    @ParameterizedTest
    @MethodSource("everyEventType")
    void carriesItsTypeInTheBodyAndItsRoutingNowhere(DomainEvent event) {
        String json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"eventType\"").doesNotContain("topic");
    }

    @Test
    void namesTheTypeWithTheConstantTheOutboxWillStore() {
        String json = mapper.writeValueAsString(
                SeatsHeld.of(BOOKING_ID, SHOW_ID, SEAT_IDS, 9000L, Instant.now()));

        // Literal, not SeatsHeld.TYPE: this fails if the constant drifts away from the wire
        // format, which is exactly the change that would break a deployed consumer.
        assertThat(json).contains("\"eventType\":\"SeatsHeld\"");
    }

    @Test
    void keepsEveryFieldThroughTheRoundTrip() {
        SeatsHeld held = SeatsHeld.of(BOOKING_ID, SHOW_ID, SEAT_IDS, 9000L, Instant.now().plusSeconds(600));

        DomainEvent restored = mapper.readValue(mapper.writeValueAsString(held), DomainEvent.class);

        assertThat(restored).isInstanceOf(SeatsHeld.class);
        SeatsHeld back = (SeatsHeld) restored;
        assertThat(back.eventId()).isEqualTo(held.eventId());
        assertThat(back.bookingId()).isEqualTo(held.bookingId());
        assertThat(back.showId()).isEqualTo(held.showId());
        assertThat(back.seatIds()).containsExactlyElementsOf(SEAT_IDS);
        assertThat(back.amountCents()).isEqualTo(9000L);
        assertThat(back.holdExpiresAt()).isEqualTo(held.holdExpiresAt());
        assertThat(back.occurredAt()).isEqualTo(held.occurredAt());
        assertThat(back.schemaVersion()).isEqualTo(SeatsHeld.SCHEMA_VERSION);
    }

    @Test
    void routesEachTypeToItsOwnTopic() {
        assertThat(BookingRequested.of(BOOKING_ID, SHOW_ID, SEAT_IDS, "cust-42").topic())
                .isEqualTo(Topics.BOOKINGS);
        assertThat(SeatsHeld.of(BOOKING_ID, SHOW_ID, SEAT_IDS, 1L, Instant.now()).topic())
                .isEqualTo(Topics.SEATS);
        assertThat(PaymentAuthorized.of(BOOKING_ID, 1L, "ref").topic())
                .isEqualTo(Topics.PAYMENTS);
        assertThat(BookingConfirmed.of(BOOKING_ID, SHOW_ID, SEAT_IDS).topic())
                .isEqualTo(Topics.BOOKINGS);
    }

    @Test
    void rejectsAnUnknownEventType() {
        String fromAFutureService = """
                {"eventType":"SeatsRejected","eventId":"%s","bookingId":"%s",
                 "occurredAt":"2026-01-01T00:00:00Z","schemaVersion":1}
                """.formatted(UUID.randomUUID(), BOOKING_ID);

        assertThatThrownBy(() -> mapper.readValue(fromAFutureService, DomainEvent.class))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining("SeatsRejected");
    }

}
