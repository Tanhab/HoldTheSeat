package com.tanhab.holdtheseat.booking.security;

import com.tanhab.holdtheseat.booking.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the full filter chain against a real database. Carries its own key so the test
 * does not depend on the dev default in application.yml.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "holdtheseat.api-key.hash=3a3f2bdb182b4fecb00955e6f7303dca0ed4eefca411a0a3fb9e9bb36a08daa5")
class BookingApiSecurityTest extends AbstractIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String VALID_KEY = "hts_test_key";
    private static final String UNKNOWN_BOOKING = "00000000-0000-0000-0000-000000000001";

    private static final String VALID_REQUEST = """
            {
              "showId": "11111111-1111-1111-1111-111111111111",
              "seatIds": ["22222222-2222-2222-2222-222222222222"],
              "customerId": "cust-42",
              "amountCents": 4500
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UNKNOWN_BOOKING))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void requestWithWrongKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UNKNOWN_BOOKING).header(API_KEY_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeRequestWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsReachableWithoutKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void validKeyReachesTheControllerAndReturnsNotFoundForUnknownBooking() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UNKNOWN_BOOKING).header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Booking Not Found"));
    }

    @Test
    void bookingRoundTripsWithValidKey() throws Exception {
        MvcResult created = mockMvc.perform(post("/bookings")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/bookings/{id}", id).header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerId").value("cust-42"))
                .andExpect(jsonPath("$.amountCents").value(4500))
                .andExpect(jsonPath("$.seatIds.length()").value(1));
    }

}
