package com.smartparking.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService service = new PricingService(new BigDecimal("2.50"));

    @Test
    void appliesSurgeMultiplierForHighPredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.85);

        // 2 hours * 2.50 * 1.5 = 7.50
        assertThat(cost).isEqualByComparingTo("7.50");
    }

    @Test
    void appliesDiscountForLowPredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.20);

        // 2 hours * 2.50 * 0.8 = 4.00
        assertThat(cost).isEqualByComparingTo("4.00");
    }

    @Test
    void appliesStandardRateForModeratePredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(1, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.5);

        // 1 hour * 2.50 * 1.0 = 2.50
        assertThat(cost).isEqualByComparingTo("2.50");
    }
}
