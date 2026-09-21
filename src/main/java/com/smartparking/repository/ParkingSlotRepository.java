package com.smartparking.repository;

import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findByLocationIdAndStatus(Long locationId, SlotStatus status);
    List<ParkingSlot> findByLocationId(Long locationId);
}
