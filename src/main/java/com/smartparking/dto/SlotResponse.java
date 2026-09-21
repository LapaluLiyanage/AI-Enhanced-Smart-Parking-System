package com.smartparking.dto;

import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;

public record SlotResponse(Long id, Long locationId, int slotNumber, SlotStatus status) {
    public static SlotResponse from(ParkingSlot slot) {
        return new SlotResponse(slot.getId(), slot.getLocation().getId(), slot.getSlotNumber(), slot.getStatus());
    }
}
