package com.smartparking.service;

import com.smartparking.ai.PredictionCache;
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
    @Mock private PricingService pricingService;
    @Mock private PredictionCache predictionCache;
    @Mock private com.smartparking.repository.LocationRepository locationRepository;
    @Mock private AnomalyDetectionService anomalyDetectionService;

    private BookingService service() {
        return new BookingService(bookingRepository, slotRepository, userRepository, pricingService, predictionCache, locationRepository, anomalyDetectionService);
    }

    @Test
    void createBookingSucceedsWhenNoOverlap() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());
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

        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(OverlappingBookingException.class);
    }

    @Test
    void createBookingRejectsEndTimeNotAfterStartTime() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.minus(1, ChronoUnit.HOURS);

        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be after startTime");
    }

    @Test
    void createBookingRejectsEndTimeEqualToStartTime() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);

        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, start))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createBookingUsesConfiguredAnomalyWindowForLookback() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(anomalyDetectionService.getWindowMinutes()).thenReturn(42);
        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createBooking("a@b.com", 1L, start, end);

        org.mockito.ArgumentCaptor<Instant> cutoffCaptor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(bookingRepository)
                .findByUserEmailAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq("a@b.com"), cutoffCaptor.capture());

        Instant expectedCutoff = Instant.now().minus(java.time.Duration.ofMinutes(42));
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    void cancelBookingFreesSlotWhenNoOtherActiveBookingsRemain() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countByStatusInAndSlotIdAndIdNot(any(), any(), any())).thenReturn(0L);

        service().cancelBooking("a@b.com", 1L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void cancelBookingLeavesSlotReservedWhenOtherActiveBookingRemains() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countByStatusInAndSlotIdAndIdNot(any(), any(), any())).thenReturn(1L);

        service().cancelBooking("a@b.com", 1L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.RESERVED);
    }

    @Test
    void createBookingRejectsOccupiedSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.OCCUPIED);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());
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
        when(pricingService.calculateCost(any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new java.math.BigDecimal("5.00"));
        when(predictionCache.get(any())).thenReturn(java.util.Optional.empty());

        Booking result = service().checkOut("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(result.getTotalCost()).isEqualByComparingTo("5.00");
    }
}
