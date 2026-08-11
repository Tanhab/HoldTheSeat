package com.tanhab.holdtheseat.seat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShowNotFoundException.class)
    public ProblemDetail handleShowNotFound(ShowNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Show Not Found");
        return pd;
    }

    /**
     * 400 rather than 404: the show exists, so the request itself is malformed — it names
     * seats that were never part of it. Nothing about retrying makes it succeed.
     */
    @ExceptionHandler(UnknownSeatException.class)
    public ProblemDetail handleUnknownSeat(UnknownSeatException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Unknown Seats");
        pd.setProperty("unknownSeatIds", ex.seatIds());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Validation Failed");
        Map<String, String> errors = new HashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("errors", errors);
        return pd;
    }

}
