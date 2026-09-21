package com.smartparking.controller;

import com.smartparking.dto.BookingResponse;
import com.smartparking.dto.CreateBookingRequest;
import com.smartparking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request, Authentication auth) {
        var booking = bookingService.createBooking(
                auth.getName(), request.slotId(), request.startTime(), request.endTime());
        return ResponseEntity.status(201).body(BookingResponse.from(booking));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication auth) {
        bookingService.cancelBooking(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
