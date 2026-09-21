package com.smartparking.service;

import com.smartparking.entity.*;
import com.smartparking.exception.AnomalyDetectedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyDetectionServiceTest {

    private final AnomalyDetectionService service = new AnomalyDetectionService(5, 5);

    @Test
    void allowsUserUnderThreshold() {
        List<Booking> recent = bookingsCreatedNow(4);

        assertThatCode(() -> service.checkForAbuse("a@b.com", recent)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUserOverThreshold() {
        List<Booking> recent = bookingsCreatedNow(6);

        assertThatThrownBy(() -> service.checkForAbuse("a@b.com", recent))
                .isInstanceOf(AnomalyDetectedException.class);
    }

    private List<Booking> bookingsCreatedNow(int count) {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)))
                .toList();
    }
}
