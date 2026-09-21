package com.smartparking.service;

import com.smartparking.entity.*;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.exception.SlotUnavailableException;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ParkingSlotRepository slotRepository;
    @Mock private UserRepository userRepository;

    private BookingService service() {
        return new BookingService(bookingRepository, slotRepository, userRepository);
    }

    @Test
    void createBookingSucceedsWhenNoOverlap() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking booking = service().createBooking("a@b.com", 1L, start, end);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getSlot()).isEqualTo(slot);
    }

    @Test
    void createBookingRejectsOverlap() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Booking existing = new Booking(user, slot, start, end);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(OverlappingBookingException.class);
    }

    @Test
    void createBookingRejectsOccupiedSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.OCCUPIED);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void confirmMovesBookingFromPendingToConfirmed() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().confirmBooking("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isNotNull();
    }

    @Test
    void checkInMovesConfirmedToActiveAndOccupiesSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().checkIn("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
    }

    @Test
    void checkInRejectsBookingNotYetConfirmed() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now(), Instant.now().plusSeconds(3600));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service().checkIn("a@b.com", 1L))
                .isInstanceOf(com.smartparking.exception.InvalidBookingStateException.class);
    }

    @Test
    void checkOutMovesActiveToCompletedAndFreesSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.OCCUPIED);
        Booking booking = new Booking(user, slot, Instant.now().minusSeconds(3600), Instant.now());
        booking.setStatus(BookingStatus.ACTIVE);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().checkOut("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }
}
