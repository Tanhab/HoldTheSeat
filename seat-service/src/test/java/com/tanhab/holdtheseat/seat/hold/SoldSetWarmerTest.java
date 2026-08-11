package com.tanhab.holdtheseat.seat.hold;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SoldSetWarmerTest extends AbstractIntegrationTest {

    private static final UUID DEMO_SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT_A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SEAT_A2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID SEAT_B1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Autowired
    private SoldSetWarmer warmer;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbcClient;

    // These tests write durable SOLD rows on purpose, so they cannot roll back the way the
    // repository tests do — the warmer has to read committed data.
    @AfterEach
    void resetSeatsAndRedis() {
        jdbcClient.sql("UPDATE seats SET status = 'AVAILABLE' WHERE show_id = :showId")
                .param("showId", DEMO_SHOW)
                .update();
        redis.delete(HoldKeys.sold(DEMO_SHOW));
    }

    @Test
    void loadsSoldSeatsIntoRedis() {
        seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1, SEAT_A2));

        warmer.warmSoldSets();

        String key = HoldKeys.sold(DEMO_SHOW);
        assertThat(redis.opsForSet().isMember(key, SEAT_A1.toString())).isTrue();
        assertThat(redis.opsForSet().isMember(key, SEAT_A2.toString())).isTrue();
        assertThat(redis.opsForSet().size(key)).isEqualTo(2);
    }

    @Test
    void leavesAvailableSeatsOutOfTheSet() {
        seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1));

        warmer.warmSoldSets();

        assertThat(redis.opsForSet().isMember(HoldKeys.sold(DEMO_SHOW), SEAT_B1.toString())).isFalse();
    }

    @Test
    void handlesAShowWithNothingSold() {
        warmer.warmSoldSets();

        assertThat(redis.hasKey(HoldKeys.sold(DEMO_SHOW))).isFalse();
    }

    @Test
    void discardsStaleMembersRatherThanToppingUp() {
        String key = HoldKeys.sold(DEMO_SHOW);
        redis.opsForSet().add(key, SEAT_B1.toString());
        seatRepository.markSold(DEMO_SHOW, List.of(SEAT_A1));

        warmer.warmSoldSets();

        assertThat(redis.opsForSet().members(key)).containsExactly(SEAT_A1.toString());
    }

}
