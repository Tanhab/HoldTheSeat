package com.tanhab.holdtheseat.seat.controller;

import com.tanhab.holdtheseat.seat.domain.SeatMapStatus;
import com.tanhab.holdtheseat.seat.dto.SeatMapEntry;
import com.tanhab.holdtheseat.seat.dto.SeatMapResponse;
import com.tanhab.holdtheseat.seat.exception.ShowNotFoundException;
import com.tanhab.holdtheseat.seat.service.SeatMapService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShowSeatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ShowSeatControllerTest {

    private static final UUID SHOW = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatMapService seatMapService;

    @Test
    void returnsTheMap() throws Exception {
        Mockito.when(seatMapService.mapForShow(SHOW)).thenReturn(new SeatMapResponse(
                SHOW,
                List.of(new SeatMapEntry(SEAT, "A", 1, 5000, SeatMapStatus.AVAILABLE))));

        mockMvc.perform(get("/shows/{showId}/seats", SHOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(SHOW.toString()))
                .andExpect(jsonPath("$.seats[0].id").value(SEAT.toString()))
                .andExpect(jsonPath("$.seats[0].row").value("A"))
                .andExpect(jsonPath("$.seats[0].number").value(1))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"));
    }

    @Test
    void unknownShowReturnsNotFound() throws Exception {
        Mockito.when(seatMapService.mapForShow(SHOW)).thenThrow(new ShowNotFoundException(SHOW));

        mockMvc.perform(get("/shows/{showId}/seats", SHOW))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Show Not Found"));
    }

}
