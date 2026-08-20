package com.tanhab.holdtheseat.payment.service;

import com.tanhab.holdtheseat.events.PaymentAuthorized;
import com.tanhab.holdtheseat.events.PaymentFailed;
import com.tanhab.holdtheseat.events.SeatsHeld;
import com.tanhab.holdtheseat.payment.domain.Payment;
import com.tanhab.holdtheseat.payment.domain.PaymentStatus;
import com.tanhab.holdtheseat.payment.gateway.GatewayResult;
import com.tanhab.holdtheseat.payment.gateway.MockCardGateway;
import com.tanhab.holdtheseat.payment.outbox.OutboxRepository;
import com.tanhab.holdtheseat.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final MockCardGateway gateway;
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(MockCardGateway gateway,
                          PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Charges the amount the seat service quoted. The payment row and the event announcing it
     * are written in the caller's transaction, alongside the dedup row.
     */
    public void authorize(SeatsHeld held) {
        GatewayResult result = gateway.authorize(held.bookingId(), held.customerId(), held.amountCents());

        if (!result.approved()) {
            Payment payment = paymentRepository.insert(
                    held.bookingId(), held.amountCents(), PaymentStatus.FAILED, null);
            PaymentFailed failed = PaymentFailed.of(payment.bookingId(), payment.amountCents(), result.declineReason());
            outboxRepository.append(failed.bookingId(), PaymentFailed.TYPE, failed.topic(),
                    objectMapper.writeValueAsString(failed));
            return;

        }

        Payment payment = paymentRepository.insert(
                held.bookingId(), held.amountCents(), PaymentStatus.AUTHORIZED, result.reference());

        PaymentAuthorized authorized = PaymentAuthorized.of(
                payment.bookingId(), payment.amountCents(), payment.gatewayRef());
        outboxRepository.append(authorized.bookingId(), PaymentAuthorized.TYPE, authorized.topic(),
                objectMapper.writeValueAsString(authorized));

        log.info("Authorized {} cents for booking {}", payment.amountCents(), payment.bookingId());
    }

}
