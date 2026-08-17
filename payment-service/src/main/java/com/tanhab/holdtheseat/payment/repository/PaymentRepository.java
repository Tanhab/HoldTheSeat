package com.tanhab.holdtheseat.payment.repository;

import com.tanhab.holdtheseat.payment.domain.Payment;
import com.tanhab.holdtheseat.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private static final String COLUMNS = "id, booking_id, amount_cents, status, gateway_ref, created_at";

    private static final RowMapper<Payment> PAYMENT_MAPPER = (rs, rowNum) -> new Payment(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getLong("amount_cents"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("gateway_ref"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcClient jdbcClient;

    public PaymentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Fails on the unique booking_id if this booking has already been charged. That is
     * deliberate: a second charge is not something to recover from quietly.
     */
    public Payment insert(UUID bookingId, long amountCents, PaymentStatus status, String gatewayRef) {
        return jdbcClient.sql("""
                        INSERT INTO payments (booking_id, amount_cents, status, gateway_ref)
                        VALUES (:bookingId, :amountCents, :status, :gatewayRef)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("bookingId", bookingId)
                .param("amountCents", amountCents)
                .param("status", status.name())
                .param("gatewayRef", gatewayRef)
                .query(PAYMENT_MAPPER)
                .single();
    }

    public Optional<Payment> findByBookingId(UUID bookingId) {
        return jdbcClient.sql("SELECT %s FROM payments WHERE booking_id = :bookingId".formatted(COLUMNS))
                .param("bookingId", bookingId)
                .query(PAYMENT_MAPPER)
                .optional();
    }

}
