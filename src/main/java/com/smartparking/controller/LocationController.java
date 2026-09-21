package com.smartparking.controller;

import com.smartparking.dto.LocationResponse;
import com.smartparking.dto.SlotResponse;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;

    public LocationController(LocationRepository locationRepository, ParkingSlotRepository slotRepository) {
        this.locationRepository = locationRepository;
        this.slotRepository = slotRepository;
    }

    @GetMapping
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(LocationResponse::from).toList();
    }

    @GetMapping("/{id}/slots")
    public List<SlotResponse> listSlots(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean available) {
        var slots = Boolean.TRUE.equals(available)
                ? slotRepository.findByLocationIdAndStatus(id, SlotStatus.AVAILABLE)
                : slotRepository.findByLocationId(id);
        return slots.stream().map(SlotResponse::from).toList();
    }
}
