package com.tanhab.holdtheseat.seat.controller;

import com.tanhab.holdtheseat.seat.dto.SeatMapResponse;
import com.tanhab.holdtheseat.seat.service.SeatMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowSeatController {

    private final SeatMapService seatMapService;

    public ShowSeatController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/{showId}/seats")
    public ResponseEntity<SeatMapResponse> seats(@PathVariable UUID showId) {
        return ResponseEntity.ok(seatMapService.mapForShow(showId));
    }

}
