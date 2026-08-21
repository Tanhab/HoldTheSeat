package com.tanhab.holdtheseat.booking.timeline;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.BookingTimelineResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.dto.TimelineEntryResponse;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.BookingCancelled;
import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingExpired;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.CancellationReason;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "holdtheseat.expiry.after=1m")
class BookingTimelineTest extends AbstractIntegrationTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingTimelineService timelineService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createRecordsExactlyOneBookingRequested() {
        BookingResponse created = createBooking();

        BookingTimelineResponse timeline = timelineService.timelineFor(created.id());
        assertThat(timeline.events()).hasSize(1);
        assertThat(timeline.events().getFirst().eventType()).isEqualTo(BookingRequested.TYPE);

        BookingRequested event = readOutbox(created.id(), BookingRequested.class);
        assertThat(timeline.events().getFirst().eventId()).isEqualTo(event.eventId());
    }

    @Test
    void confirmAppendsBookingConfirmed() {
        BookingResponse pending = createBooking();
        bookingService.confirm(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));

        assertThat(typesFor(pending.id()))
                .containsExactly(BookingRequested.TYPE, BookingConfirmed.TYPE);
    }

    @Test
    void cancelAppendsBookingCancelled() {
        BookingResponse pending = createBooking();
        bookingService.cancel(pending.id(), CancellationReason.PAYMENT_FAILED);

        assertThat(typesFor(pending.id()))
                .containsExactly(BookingRequested.TYPE, BookingCancelled.TYPE);
    }

    @Test
    void expireAppendsBookingExpired() {
        BookingResponse pending = createBooking();
        backdateCreatedAt(pending.id(), "2 minutes");
        assertThat(bookingService.expireStale()).isEqualTo(1);

        assertThat(typesFor(pending.id()))
                .containsExactly(BookingRequested.TYPE, BookingExpired.TYPE);
    }

    @Test
    void unknownBookingIsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> timelineService.timelineFor(missing))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void confirmThatMovesNothingDoesNotDuplicateTimeline() {
        BookingResponse pending = createBooking();
        bookingService.confirm(PaymentAuthorized.of(pending.id(), 5000L, "mock-ref"));
        bookingService.confirm(PaymentAuthorized.of(pending.id(), 9999L, "again"));

        List<TimelineEntryResponse> events = timelineService.timelineFor(pending.id()).events();
        assertThat(events).hasSize(2);
        assertThat(events.stream().filter(e -> e.eventType().equals(BookingConfirmed.TYPE))).hasSize(1);
    }

    private BookingResponse createBooking() {
        return bookingService.create(new CreateBookingRequest(SHOW, List.of(SEAT), "cust-timeline"));
    }

    private List<String> typesFor(UUID bookingId) {
        return timelineService.timelineFor(bookingId).events().stream()
                .map(TimelineEntryResponse::eventType)
                .toList();
    }

    private void backdateCreatedAt(UUID bookingId, String interval) {
        jdbcClient.sql("""
                        UPDATE bookings
                        SET created_at = now() - cast(:interval as interval)
                        WHERE id = :id
                        """)
                .param("interval", interval)
                .param("id", bookingId)
                .update();
    }

    private <T extends DomainEvent> T readOutbox(UUID bookingId, Class<T> type) {
        String payload = jdbcClient.sql("""
                        SELECT payload::text FROM outbox
                        WHERE aggregate_id = :id
                        ORDER BY created_at
                        LIMIT 1
                        """)
                .param("id", bookingId)
                .query(String.class)
                .single();
        return type.cast(objectMapper.readValue(payload, DomainEvent.class));
    }

}
