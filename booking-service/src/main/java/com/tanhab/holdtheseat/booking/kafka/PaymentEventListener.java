package com.tanhab.holdtheseat.booking.kafka;

import com.tanhab.holdtheseat.booking.inbox.ProcessedEventRepository;
import com.tanhab.holdtheseat.booking.service.BookingService;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.PaymentAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventListener {

    static final String CONSUMER_GROUP = "booking-service";

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final BookingService bookingService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(BookingService bookingService,
                                ProcessedEventRepository processedEvents,
                                ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "#{T(com.tanhab.holdtheseat.events.Topics).PAYMENTS}", groupId = CONSUMER_GROUP)
    public void onPaymentEvent(String payload) {
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);

        switch (event) {
            case PaymentAuthorized authorized -> {
                if (!processedEvents.claim(CONSUMER_GROUP, authorized.eventId())) {
                    return;
                }
                bookingService.confirm(authorized);
            }
            default -> log.debug("Not handled here: {}", event.getClass().getSimpleName());
        }
    }

}
