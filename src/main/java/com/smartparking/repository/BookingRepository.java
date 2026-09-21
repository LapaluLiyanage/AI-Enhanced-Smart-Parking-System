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

    List<Booking> findByUserEmailAndCreatedAtAfter(String email, Instant cutoff);

    // Native query: the brief's JPQL form (FUNCTION('EXTRACT', HOUR FROM ...))
    // does not parse under Hibernate 6's HQL grammar (FUNCTION() args must be
    // expressions, not "HOUR FROM x"), and ISODOW is a Postgres-specific
    // EXTRACT field with no portable JPQL equivalent, so this is expressed as
    // a native SQL query instead.
    @Query(value = """
        SELECT EXTRACT(HOUR FROM b.start_time) as hourOfDay,
               EXTRACT(ISODOW FROM b.start_time) as dayOfWeek,
               COUNT(*) as bookingCount
        FROM bookings b
        JOIN parking_slots s ON b.slot_id = s.id
        WHERE s.location_id = :locationId
        GROUP BY EXTRACT(HOUR FROM b.start_time), EXTRACT(ISODOW FROM b.start_time)
        """, nativeQuery = true)
    List<Object[]> aggregateHourlyBookingCounts(@Param("locationId") Long locationId);
}
