package com.tanhab.holdtheseat.seat.repository;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.domain.Show;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ShowRepositoryTest extends AbstractIntegrationTest {

    private static final UUID DEMO_SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ShowRepository showRepository;

    @Test
    void findsTheSeededDemoShow() {
        Optional<Show> found = showRepository.findById(DEMO_SHOW);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Hold The Seat — Opening Night");
        assertThat(found.get().startsAt()).isEqualTo(Instant.parse("2026-12-31T19:30:00Z"));
        assertThat(found.get().createdAt()).isNotNull();
    }

    @Test
    void returnsEmptyForAnUnknownShow() {
        assertThat(showRepository.findById(UUID.randomUUID())).isEmpty();
    }

}
