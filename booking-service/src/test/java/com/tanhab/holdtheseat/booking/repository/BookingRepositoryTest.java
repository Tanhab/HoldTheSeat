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

        Booking inserted = bookingRepository.insert(showId, seatIds, "customer-1", 4500L);

        assertThat(inserted.id()).isNotNull();
        assertThat(inserted.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(inserted.createdAt()).isNotNull();

        Optional<Booking> found = bookingRepository.findById(inserted.id());

        assertThat(found).hasValueSatisfying(booking -> {
            assertThat(booking.showId()).isEqualTo(showId);
            assertThat(booking.seatIds()).containsExactlyElementsOf(seatIds);
            assertThat(booking.customerId()).isEqualTo("customer-1");
            assertThat(booking.amountCents()).isEqualTo(4500L);
        });
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(bookingRepository.findById(UUID.randomUUID())).isEmpty();
    }

}
