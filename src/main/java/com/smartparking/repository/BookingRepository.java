package com.smartparking.repository;

import com.smartparking.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM Booking b
        WHERE b.slot.id = :slotId
          AND b.status IN ('PENDING', 'CONFIRMED', 'ACTIVE')
          AND b.startTime < :end
          AND b.endTime > :start
        """)
    List<Booking> findActiveOverlapping(
            @Param("slotId") Long slotId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<Booking> findByStatusAndCreatedAtBefore(
            com.smartparking.entity.BookingStatus status, Instant cutoff);
}
