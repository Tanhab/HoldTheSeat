package com.tanhab.holdtheseat.seat.kafka;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Hard problem #6 end-to-end over Kafka. {@code SeatSettlementServiceTest} already covers
 * the unit shape; this is the same claim through a real broker:
 *
 * <p>{@code BookingRequested} → held → wait out the TTL → {@code BookingConfirmed} →
 * seats stay AVAILABLE, {@code sold:} unchanged, nothing sold to a booking that no longer
 * holds anything.
 *
 * <p>Defence #1 (booking PENDING guard) lives in {@code ExpirySweeperTest}. This test is
 * defence #2 — {@code validate-hold.lua} refuses a dead hold before settle.
 */
@TestPropertySource(properties = "holdtheseat.hold.ttl=2s")
class LatePaymentTest extends AbstractIntegrationTest {
}
