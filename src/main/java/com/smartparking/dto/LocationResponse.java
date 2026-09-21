package com.smartparking.dto;

import com.smartparking.entity.Location;

public record LocationResponse(Long id, String name, String address, int totalSlots) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getAddress(), location.getTotalSlots());
    }
}
