package com.tanhab.holdtheseat.seat.controller;

import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.exception.UnknownSeatException;
import com.tanhab.holdtheseat.seat.hold.HoldOutcome;
import com.tanhab.holdtheseat.seat.hold.HoldProperties;
import com.tanhab.holdtheseat.seat.service.SeatHoldService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HoldController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(HoldControllerTest.TestConfig.class)
class HoldControllerTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOKING = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SEAT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private static final String VALID_REQUEST = """
            {
              "showId": "11111111-1111-1111-1111-111111111111",
              "bookingId": "22222222-2222-2222-2222-222222222222",
              "seatIds": ["aaaaaaaa-0000-0000-0000-000000000001"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatHoldService seatHoldService;

    @Test
    void grantedHoldReturnsCreatedWithTheWindow() throws Exception {
        Mockito.when(seatHoldService.hold(any(), any(), anyList())).thenReturn(HoldOutcome.allSeatsHeld());

        mockMvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(BOOKING.toString()))
                .andExpect(jsonPath("$.showId").value(SHOW.toString()))
                .andExpect(jsonPath("$.seatIds[0]").value(SEAT.toString()))
                .andExpect(jsonPath("$.heldUntil").exists());
    }

    @Test
    void refusedHoldReturnsConflictNamingTheSeats() throws Exception {
        Mockito.when(seatHoldService.hold(any(), any(), anyList()))
                .thenReturn(HoldOutcome.blockedBy(List.of(SEAT)));

        mockMvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Seats Unavailable"))
                .andExpect(jsonPath("$.conflictingSeatIds[0]").value(SEAT.toString()));
    }

    @Test
    void unknownShowReturnsNotFound() throws Exception {
        Mockito.when(seatHoldService.hold(any(), any(), anyList()))
                .thenThrow(new ShowNotFoundException(SHOW));

        mockMvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Show Not Found"));
    }

    @Test
    void seatsThatBelongToNoShowAreABadRequest() throws Exception {
        Mockito.when(seatHoldService.hold(any(), any(), anyList()))
                .thenThrow(new UnknownSeatException(SHOW, List.of(SEAT)));

        mockMvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown Seats"))
                .andExpect(jsonPath("$.unknownSeatIds[0]").value(SEAT.toString()));
    }

    @Test
    void emptySeatListIsRejectedBeforeTheServiceIsCalled() throws Exception {
        String noSeats = """
                {
                  "showId": "11111111-1111-1111-1111-111111111111",
                  "bookingId": "22222222-2222-2222-2222-222222222222",
                  "seatIds": []
                }
                """;

        mockMvc.perform(post("/holds").contentType(MediaType.APPLICATION_JSON).content(noSeats))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seatIds").exists());

        Mockito.verifyNoInteractions(seatHoldService);
    }

    @Test
    void releaseReturnsNoContentEvenWhenNothingWasHeld() throws Exception {
        Mockito.when(seatHoldService.release(BOOKING)).thenReturn(0L);

        mockMvc.perform(delete("/holds/{bookingId}", BOOKING))
                .andExpect(status().isNoContent());
    }

    static class TestConfig {
        @Bean
        HoldProperties holdProperties() {
            return new HoldProperties(Duration.ofMinutes(10));
        }
    }

}
