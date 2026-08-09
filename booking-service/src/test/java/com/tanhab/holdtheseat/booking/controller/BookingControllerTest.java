package com.tanhab.holdtheseat.booking.controller;

import com.tanhab.holdtheseat.booking.domain.BookingStatus;
import com.tanhab.holdtheseat.booking.dto.BookingResponse;
import com.tanhab.holdtheseat.booking.dto.CreateBookingRequest;
import com.tanhab.holdtheseat.booking.exception.BookingNotFoundException;
import com.tanhab.holdtheseat.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only: no database, and addFilters = false strips the security chain because
 * authentication is covered by BookingApiSecurityTest against the real filter chain.
 * BookingService is mocked since @WebMvcTest does not load @Service beans.
 */
@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("019fe480-c0d3-7000-8a93-145c2aa3a182");
    private static final UUID SHOW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEAT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String VALID_REQUEST = """
            {
              "showId": "11111111-1111-1111-1111-111111111111",
              "seatIds": ["22222222-2222-2222-2222-222222222222"],
              "customerId": "cust-42",
              "amountCents": 4500
            }
            """;

    private static final String INVALID_REQUEST = """
            {
              "showId": null,
              "seatIds": [],
              "customerId": "  ",
              "amountCents": -5
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void createReturnsCreatedWithLocationHeader() throws Exception {
        given(bookingService.create(any(CreateBookingRequest.class))).willReturn(pendingBooking());

        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seatIds[0]").value(SEAT_ID.toString()))
                .andReturn();

        assertThat(result.getResponse().getHeader("Location"))
                .endsWith("/bookings/" + BOOKING_ID);
    }

    @Test
    void createRejectsInvalidRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.showId").exists())
                .andExpect(jsonPath("$.errors.seatIds").exists())
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors.amountCents").exists());

        verifyNoInteractions(bookingService);
    }

    @Test
    void getReturnsBooking() throws Exception {
        given(bookingService.findById(BOOKING_ID)).willReturn(pendingBooking());

        mockMvc.perform(get("/bookings/{id}", BOOKING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.customerId").value("cust-42"));
    }

    @Test
    void getUnknownBookingReturnsProblemDetail() throws Exception {
        willThrow(new BookingNotFoundException(BOOKING_ID)).given(bookingService).findById(BOOKING_ID);

        mockMvc.perform(get("/bookings/{id}", BOOKING_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Booking Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private static BookingResponse pendingBooking() {
        return new BookingResponse(BOOKING_ID, SHOW_ID, List.of(SEAT_ID), "cust-42", 4500L,
                BookingStatus.PENDING, Instant.parse("2026-08-09T03:11:11Z"));
    }

}
