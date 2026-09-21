package com.smartparking.scheduler;

import com.smartparking.entity.*;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.service.BookingService;
import com.smartparking.service.PricingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpiryTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ParkingSlotRepository slotRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingService pricingService;

    @Test
    void expiresStalePendingBookingsAndFreesSlots() {
        BookingService service = new BookingService(bookingRepository, slotRepository, userRepository, pricingService);

        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking stale = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findByStatusAndCreatedAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));

        int expiredCount = service.expireStalePendingBookings(Duration.ofMinutes(10));

        assertThat(expiredCount).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }
}
