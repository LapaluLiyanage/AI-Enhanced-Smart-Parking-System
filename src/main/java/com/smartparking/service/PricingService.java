package com.smartparking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class PricingService {

    private static final BigDecimal HIGH_OCCUPANCY_THRESHOLD = new BigDecimal("0.75");
    private static final BigDecimal LOW_OCCUPANCY_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal SURGE_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DISCOUNT_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal STANDARD_MULTIPLIER = BigDecimal.ONE;

    private final BigDecimal baseHourlyRate;

    public PricingService(@Value("${app.pricing.base-hourly-rate}") BigDecimal baseHourlyRate) {
        this.baseHourlyRate = baseHourlyRate;
    }

    public BigDecimal calculateCost(Instant start, Instant end, double predictedOccupancyPct) {
        BigDecimal hours = BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal occupancy = BigDecimal.valueOf(predictedOccupancyPct);
        BigDecimal multiplier = surgeMultiplierFor(occupancy);
        return hours.multiply(baseHourlyRate).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal surgeMultiplierFor(BigDecimal predictedOccupancyPct) {
        if (predictedOccupancyPct.compareTo(HIGH_OCCUPANCY_THRESHOLD) > 0) {
            return SURGE_MULTIPLIER;
        }
        if (predictedOccupancyPct.compareTo(LOW_OCCUPANCY_THRESHOLD) < 0) {
            return DISCOUNT_MULTIPLIER;
        }
        return STANDARD_MULTIPLIER;
    }
}
