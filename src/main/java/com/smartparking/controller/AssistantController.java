package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.dto.AssistantBookingRequest;
import com.smartparking.dto.BookingResponse;
import com.smartparking.repository.LocationRepository;
import com.smartparking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final AiAssistantService aiAssistantService;
    private final BookingService bookingService;
    private final LocationRepository locationRepository;

    public AssistantController(AiAssistantService aiAssistantService, BookingService bookingService,
                                LocationRepository locationRepository) {
        this.aiAssistantService = aiAssistantService;
        this.bookingService = bookingService;
        this.locationRepository = locationRepository;
    }

    @PostMapping("/book")
    public BookingResponse book(@Valid @RequestBody AssistantBookingRequest request, Authentication auth) {
        var knownNames = locationRepository.findAll().stream().map(l -> l.getName()).toList();
        var intent = aiAssistantService.parseBookingIntent(request.message(), knownNames);
        var endTime = intent.startTime().plus(Duration.ofMinutes(intent.durationMinutes()));
        var booking = bookingService.createBookingByLocationName(auth.getName(), intent.locationName(), intent.startTime(), endTime);
        return BookingResponse.from(booking);
    }
}
