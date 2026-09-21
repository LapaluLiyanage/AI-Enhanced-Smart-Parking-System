package com.smartparking.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionCacheTest {

    @Test
    void returnsCachedValueBeforeExpiry() {
        PredictionCache cache = new PredictionCache(Duration.ofMinutes(5));
        OccupancyPrediction prediction = new OccupancyPrediction(0.6, "test");

        cache.put(1L, prediction);

        assertThat(cache.get(1L)).contains(prediction);
    }

    @Test
    void returnsEmptyForUncachedLocation() {
        PredictionCache cache = new PredictionCache(Duration.ofMinutes(5));

        assertThat(cache.get(99L)).isEmpty();
    }

    @Test
    void returnsEmptyAfterTtlExpires() throws InterruptedException {
        PredictionCache cache = new PredictionCache(Duration.ofMillis(50));
        cache.put(1L, new OccupancyPrediction(0.6, "test"));

        Thread.sleep(100);

        assertThat(cache.get(1L)).isEmpty();
    }
}
