package com.smartparking.repository;

import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findByLocationIdAndStatus(Long locationId, SlotStatus status);
    List<ParkingSlot> findByLocationId(Long locationId);

    /**
     * Locks the slot row itself for the rest of the caller's transaction.
     * Unlike a lock on matching Booking rows (which lock nothing when no
     * booking yet exists for the slot/window), the slot row always exists,
     * so this serializes all concurrent booking attempts for the same slot
     * and closes the phantom-insert race that a Booking-only lock leaves open.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ParkingSlot s WHERE s.id = :id")
    Optional<ParkingSlot> findByIdForUpdate(@Param("id") Long id);
}
