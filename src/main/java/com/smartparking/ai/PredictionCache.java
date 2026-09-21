package com.smartparking.ai;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PredictionCache {

    private record Entry(OccupancyPrediction prediction, Instant expiresAt) {}

    private final Duration ttl;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    public PredictionCache() {
        this(Duration.ofMinutes(5));
    }

    public PredictionCache(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(Long locationId, OccupancyPrediction prediction) {
        entries.put(locationId, new Entry(prediction, Instant.now().plus(ttl)));
    }

    public Optional<OccupancyPrediction> get(Long locationId) {
        Entry entry = entries.get(locationId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(locationId);
            return Optional.empty();
        }
        return Optional.of(entry.prediction());
    }
}
