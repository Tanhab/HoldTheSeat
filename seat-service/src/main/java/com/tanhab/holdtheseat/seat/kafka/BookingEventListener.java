package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.events.BookingConfirmed;
import com.tanhab.holdtheseat.events.BookingRequested;
import com.tanhab.holdtheseat.events.DomainEvent;
import com.tanhab.holdtheseat.events.RejectionReason;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.events.SeatsRejected;
import com.tanhab.holdtheseat.seat.domain.Seat;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.exception.UnknownSeatException;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import com.tanhab.holdtheseat.seat.hold.HoldProperties;
import com.tanhab.holdtheseat.seat.inbox.ProcessedEventRepository;
import com.tanhab.holdtheseat.seat.outbox.OutboxRepository;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import com.tanhab.holdtheseat.seat.service.SeatHoldService;
import com.tanhab.holdtheseat.seat.service.SeatSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Component
public class BookingEventListener {

    static final String CONSUMER_GROUP = "seat-service";

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final SeatHoldService seatHoldService;
    private final SeatSettlementService settlementService;
    private final SeatRepository seatRepository;
    private final ProcessedEventRepository processedEvents;
    private final OutboxRepository outboxRepository;
    private final HoldProperties holdProperties;
    private final ObjectMapper objectMapper;

    public BookingEventListener(SeatHoldService seatHoldService,
                                SeatSettlementService settlementService,
                                SeatRepository seatRepository,
                                ProcessedEventRepository processedEvents,
                                OutboxRepository outboxRepository,
                                HoldProperties holdProperties,
                                ObjectMapper objectMapper) {
        this.seatHoldService = seatHoldService;
        this.settlementService = settlementService;
        this.seatRepository = seatRepository;
        this.processedEvents = processedEvents;
        this.outboxRepository = outboxRepository;
        this.holdProperties = holdProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "#{T(com.tanhab.holdtheseat.events.Topics).BOOKINGS}",
            groupId = CONSUMER_GROUP)
    public void onBookingEvent(String payload) {
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);

        switch (event) {
            case BookingRequested requested -> {
                if (!processedEvents.claim(CONSUMER_GROUP, requested.eventId())) {
                    return;
                }
                HoldOutcome outcome = null;
                try {
                    outcome = seatHoldService.hold(requested.showId(), requested.bookingId(),
                            requested.seatIds());
                } catch (UnknownSeatException e) {
                    SeatsRejected rejected = SeatsRejected.invalidRequest(requested.bookingId(), requested.showId(),
                            requested.seatIds(),
                            RejectionReason.UNKNOWN_SEATS);
                    outboxRepository.append(requested.bookingId(), SeatsRejected.TYPE, rejected.topic(),
                            objectMapper.writeValueAsString(rejected));
                } catch (ShowNotFoundException e) {
                    SeatsRejected rejected = SeatsRejected.invalidRequest(requested.bookingId(), requested.showId(),
                            requested.seatIds(),
                            RejectionReason.UNKNOWN_SHOW);
                    outboxRepository.append(requested.bookingId(), SeatsRejected.TYPE, rejected.topic(),
                            objectMapper.writeValueAsString(rejected));
                }
                if (outcome == null) {
                    return;
                }
                if (outcome.granted()) {
                    List<Seat> seats = seatRepository.findByIds(requested.showId(), requested.seatIds());
                    long total = seats.stream().mapToLong(Seat::priceCents).sum();
                    SeatsHeld seatsHeld = SeatsHeld.of(requested.bookingId(), requested.showId(),
                            requested.seatIds(), requested.customerId(), total,
                            Instant.now().plus(holdProperties.ttl()));
                    outboxRepository.append(requested.bookingId(), SeatsHeld.TYPE, seatsHeld.topic(),
                            objectMapper.writeValueAsString(seatsHeld));

                } else {
                    SeatsRejected rejected = SeatsRejected.seatsUnavailable(requested.bookingId(), requested.showId(),
                            requested.seatIds(), outcome.conflictingSeatIds());
                    outboxRepository.append(requested.bookingId(), SeatsRejected.TYPE, rejected.topic(),
                            objectMapper.writeValueAsString(rejected));
                    log.warn("Seats not granted {}", outcome.conflictingSeatIds());
                }

            }
            case BookingConfirmed confirmed -> {
                if (!processedEvents.claim(CONSUMER_GROUP, confirmed.eventId())) {
                    return;
                }
                settlementService.settle(confirmed);
            }
            default -> log.debug("Not handled here: {}", event.getClass().getSimpleName());
        }
    }

}
