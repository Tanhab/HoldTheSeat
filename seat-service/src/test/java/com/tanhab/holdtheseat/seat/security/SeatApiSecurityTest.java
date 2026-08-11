package com.tanhab.holdtheseat.seat.security;

import com.tanhab.holdtheseat.seat.AbstractIntegrationTest;
import com.tanhab.holdtheseat.seat.hold.HoldKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the full filter chain against real infrastructure. Carries its own key so the test
 * does not depend on the dev default in application.yml.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "holdtheseat.api-key.hash=3a3f2bdb182b4fecb00955e6f7303dca0ed4eefca411a0a3fb9e9bb36a08daa5")
class SeatApiSecurityTest extends AbstractIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String VALID_KEY = "hts_test_key";

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT_A1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void requestWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdRequest(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void requestWithWrongKeyIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/holds/{id}", UUID.randomUUID()).header(API_KEY_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsReachableWithoutKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void holdRoundTripsWithAValidKey() throws Exception {
        UUID booking = UUID.randomUUID();

        mockMvc.perform(post("/holds")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdRequest(booking)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.heldUntil").exists());

        assertThat(redis.opsForValue().get(HoldKeys.hold(SHOW, SEAT_A1))).isEqualTo(booking.toString());

        mockMvc.perform(delete("/holds/{id}", booking).header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNoContent());

        assertThat(redis.hasKey(HoldKeys.hold(SHOW, SEAT_A1))).isFalse();
    }

    @Test
    void aSeatAlreadyHeldAnswersConflict() throws Exception {
        mockMvc.perform(post("/holds")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdRequest(UUID.randomUUID())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/holds")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdRequest(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictingSeatIds[0]").value(SEAT_A1.toString()));
    }

    private static String holdRequest(UUID bookingId) {
        return """
                {
                  "showId": "%s",
                  "bookingId": "%s",
                  "seatIds": ["%s"]
                }
                """.formatted(SHOW, bookingId, SEAT_A1);
    }

}
