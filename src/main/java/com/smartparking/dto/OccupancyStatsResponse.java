package com.smartparking.dto;

public record OccupancyStatsResponse(
        Long locationId, long totalSlots, long available, long reserved, long occupied, double occupancyPct) {
}
