package com.tanhab.holdtheseat.payment.kafka;

import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.payment.inbox.ProcessedEventRepository;
import com.tanhab.holdtheseat.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class SeatEventListener {

    static final String CONSUMER_GROUP = "payment-service";

    private static final Logger log = LoggerFactory.getLogger(SeatEventListener.class);

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public SeatEventListener(PaymentService paymentService,
                             ProcessedEventRepository processedEvents,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "#{T(com.tanhab.holdtheseat.events.Topics).SEATS}", groupId = CONSUMER_GROUP)
    public void onSeatEvent(String payload) {
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);

        switch (event) {
            case SeatsHeld held -> {
                if (!processedEvents.claim(CONSUMER_GROUP, held.eventId())) {
                    return;
                }
                paymentService.authorize(held);
            }
            default -> log.debug("Not handled here: {}", event.getClass().getSimpleName());
        }
    }

}
