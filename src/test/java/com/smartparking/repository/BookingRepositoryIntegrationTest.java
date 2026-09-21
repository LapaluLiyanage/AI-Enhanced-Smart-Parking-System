package com.smartparking.repository;

import com.smartparking.AbstractIntegrationTest;
import com.smartparking.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @Transactional
    void findActiveOverlappingDetectsOverlapAcrossBoundaries() {
        User user = userRepository.save(new User("repo-test@example.com", "hash", Role.USER));
        Location location = locationRepository.save(new Location("Repo Test Lot", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        Instant existingStart = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant existingEnd = existingStart.plus(1, ChronoUnit.HOURS);
        bookingRepository.save(new Booking(user, slot, existingStart, existingEnd));

        // Overlaps the last 30 minutes of the existing booking.
        Instant queryStart = existingEnd.minus(30, ChronoUnit.MINUTES);
        Instant queryEnd = existingEnd.plus(30, ChronoUnit.MINUTES);

        List<Booking> overlapping = bookingRepository.findActiveOverlapping(slot.getId(), queryStart, queryEnd);

        assertThat(overlapping).hasSize(1);
    }

    @Test
    @Transactional
    void findActiveOverlappingReturnsEmptyForAdjacentNonOverlappingWindow() {
        User user = userRepository.save(new User("repo-test2@example.com", "hash", Role.USER));
        Location location = locationRepository.save(new Location("Repo Test Lot 2", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        Instant existingStart = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant existingEnd = existingStart.plus(1, ChronoUnit.HOURS);
        bookingRepository.save(new Booking(user, slot, existingStart, existingEnd));

        // Starts exactly when the existing booking ends — not an overlap.
        List<Booking> overlapping = bookingRepository.findActiveOverlapping(
                slot.getId(), existingEnd, existingEnd.plus(1, ChronoUnit.HOURS));

        assertThat(overlapping).isEmpty();
    }
}
