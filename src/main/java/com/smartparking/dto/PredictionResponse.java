package com.smartparking.dto;

public record PredictionResponse(Long locationId, double predictedOccupancyPct, String reasoning) {
}
