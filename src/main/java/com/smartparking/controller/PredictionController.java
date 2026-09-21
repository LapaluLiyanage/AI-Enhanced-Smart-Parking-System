package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.HourlyBookingCount;
import com.smartparking.ai.OccupancyPrediction;
import com.smartparking.ai.PredictionCache;
import com.smartparking.dto.PredictionResponse;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.LocationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PredictionController {

    private final AiAssistantService aiAssistantService;
    private final PredictionCache predictionCache;
    private final BookingRepository bookingRepository;
    private final LocationRepository locationRepository;

    public PredictionController(AiAssistantService aiAssistantService, PredictionCache predictionCache,
                                 BookingRepository bookingRepository, LocationRepository locationRepository) {
        this.aiAssistantService = aiAssistantService;
        this.predictionCache = predictionCache;
        this.bookingRepository = bookingRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/predictions/{locationId}")
    public PredictionResponse predict(@PathVariable Long locationId) {
        var cached = predictionCache.get(locationId);
        if (cached.isPresent()) {
            return new PredictionResponse(locationId, cached.get().predictedOccupancyPct(), cached.get().reasoning());
        }

        var location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + locationId));

        List<HourlyBookingCount> history = bookingRepository.aggregateHourlyBookingCounts(locationId).stream()
                .map(row -> new HourlyBookingCount(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue()))
                .toList();

        OccupancyPrediction prediction = aiAssistantService.predictOccupancy(location.getName(), history);
        predictionCache.put(locationId, prediction);
        return new PredictionResponse(locationId, prediction.predictedOccupancyPct(), prediction.reasoning());
    }
}
