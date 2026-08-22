package com.tanhab.holdtheseat.booking.kafka;

import com.tanhab.holdtheseat.booking.inbox.ProcessedEventRepository;
import com.tanhab.holdtheseat.booking.observability.BookingIdMdc;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.booking.timeline.BookingTimelineService;
import com.tanhab.holdtheseat.events.CancellationReason;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.SeatsRejected;
import com.tanhab.holdtheseat.events.SeatsReleased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The booking service's ear on the saga. It hears the seat service's verdict on the hold
 * (on {@code seats}) and the payment service's verdict on the charge (on {@code payments}),
 * and moves the booking to its terminal state accordingly. It produces only to {@code bookings},
 * so it never consumes a topic it writes to.
 */
@Component
public class SagaEventListener {

    static final String CONSUMER_GROUP = "booking-service";

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    private final BookingService bookingService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;
    private final BookingTimelineService bookingTimelineService;

    public SagaEventListener(BookingService bookingService,
                             ProcessedEventRepository processedEvents,
                             ObjectMapper objectMapper, BookingTimelineService bookingTimelineService) {
        this.bookingService = bookingService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.bookingTimelineService = bookingTimelineService;
    }

    @Transactional
    @KafkaListener(topics = {
            "#{T(com.tanhab.holdtheseat.events.Topics).SEATS}",
            "#{T(com.tanhab.holdtheseat.events.Topics).PAYMENTS}"
    }, groupId = CONSUMER_GROUP)
    public void onSagaEvent(String payload) {
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
        try {
            BookingIdMdc.put(event);
            switch (event) {
                case PaymentAuthorized authorized -> {
                    if (!processedEvents.claim(CONSUMER_GROUP, authorized.eventId())) {
                        return;
                    }
                    bookingTimelineService.append(authorized);
                    bookingService.confirm(authorized);
                }
                case SeatsRejected rejected -> {
                    if (!processedEvents.claim(CONSUMER_GROUP, rejected.eventId())) {
                        return;
                    }
                    bookingTimelineService.append(rejected);
                    bookingService.cancel(rejected.bookingId(), CancellationReason.SEATS_REJECTED);
                }
                case PaymentFailed failed -> {
                    if (!processedEvents.claim(CONSUMER_GROUP, failed.eventId())) {
                        return;
                    }
                    bookingTimelineService.append(failed);
                    bookingService.cancel(failed.bookingId(), CancellationReason.PAYMENT_FAILED);
                }
                case SeatsHeld held -> {
                    if (!processedEvents.claim(CONSUMER_GROUP, held.eventId())) {
                        return;
                    }
                    bookingTimelineService.append(held);
                }
                case SeatsReleased released -> {
                    if (!processedEvents.claim(CONSUMER_GROUP, released.eventId())) {
                        return;
                    }
                    bookingTimelineService.append(released);
                }
                default -> log.debug("Not handled here: {}", event.getClass().getSimpleName());
            }
        } finally {
            BookingIdMdc.clear();
        }
    }

}
