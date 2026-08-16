package com.tanhab.holdtheseat.booking.repository;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import com.tanhab.holdtheseat.booking.domain.Booking;
import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void insertedBookingRoundTrips() {
        UUID showId = UUID.randomUUID();
        List<UUID> seatIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        Booking inserted = bookingRepository.insert(showId, seatIds, "customer-1");

        assertThat(inserted.id()).isNotNull();
        assertThat(inserted.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(inserted.createdAt()).isNotNull();
        assertThat(inserted.amountCents()).isNull();

        Optional<Booking> found = bookingRepository.findById(inserted.id());

        assertThat(found).hasValueSatisfying(booking -> {
            assertThat(booking.showId()).isEqualTo(showId);
            assertThat(booking.seatIds()).containsExactlyElementsOf(seatIds);
            assertThat(booking.customerId()).isEqualTo("customer-1");
            assertThat(booking.amountCents()).isNull();
        });
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(bookingRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void confirmSetsTheStatusAndThePrice() {
        Booking pending = bookingRepository.insert(UUID.randomUUID(), List.of(UUID.randomUUID()), "customer-2");

        assertThat(bookingRepository.confirm(pending.id(), 9000L)).isEqualTo(1);

        assertThat(bookingRepository.findById(pending.id())).hasValueSatisfying(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.amountCents()).isEqualTo(9000L);
        });
    }

    /**
     * The guard that makes a redelivered PaymentAuthorized harmless: the second call finds
     * nothing in PENDING and reports that it changed nothing.
     */
    @Test
    void confirmingTwiceChangesNothingTheSecondTime() {
        Booking pending = bookingRepository.insert(UUID.randomUUID(), List.of(UUID.randomUUID()), "customer-3");
        bookingRepository.confirm(pending.id(), 9000L);

        assertThat(bookingRepository.confirm(pending.id(), 12345L)).isZero();

        assertThat(bookingRepository.findById(pending.id()))
                .hasValueSatisfying(booking -> assertThat(booking.amountCents()).isEqualTo(9000L));
    }

}
