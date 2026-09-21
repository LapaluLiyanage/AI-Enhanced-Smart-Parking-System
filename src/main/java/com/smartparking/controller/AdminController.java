package com.smartparking.controller;

import com.smartparking.dto.OccupancyStatsResponse;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ParkingSlotRepository slotRepository;
    private final LocationRepository locationRepository;

    public AdminController(ParkingSlotRepository slotRepository, LocationRepository locationRepository) {
        this.slotRepository = slotRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/locations/{id}/occupancy")
    public OccupancyStatsResponse occupancy(@PathVariable Long id) {
        var location = locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + id));
        long available = slotRepository.countByLocationIdAndStatus(id, SlotStatus.AVAILABLE);
        long reserved = slotRepository.countByLocationIdAndStatus(id, SlotStatus.RESERVED);
        long occupied = slotRepository.countByLocationIdAndStatus(id, SlotStatus.OCCUPIED);
        long total = location.getTotalSlots();
        double occupancyPct = total == 0 ? 0.0 : (double) (reserved + occupied) / total;
        return new OccupancyStatsResponse(id, total, available, reserved, occupied, occupancyPct);
    }
}
