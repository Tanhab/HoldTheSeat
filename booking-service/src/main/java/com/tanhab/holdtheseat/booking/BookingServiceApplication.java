package com.tanhab.holdtheseat.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Authentication comes from ApiKeyAuthFilter, so the default in-memory user and its
// generated startup password are dead weight.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

}
