package com.smartparking.service;

import com.smartparking.ai.OccupancyPrediction;
import com.smartparking.ai.PredictionCache;
import com.smartparking.entity.*;
import com.smartparking.exception.BookingNotFoundException;
import com.smartparking.exception.InvalidBookingStateException;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.exception.SlotUnavailableException;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final PredictionCache predictionCache;

    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository,
                           UserRepository userRepository, PricingService pricingService,
                           PredictionCache predictionCache) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.predictionCache = predictionCache;
    }

    @Transactional
    public Booking createBooking(String userEmail, Long slotId, Instant startTime, Instant endTime) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userEmail));

        // Pessimistic write lock on the slot row itself, held for the rest of
        // this transaction. The slot row always exists (unlike a Booking row,
        // which may not exist yet for this slot/window), so this is what
        // actually serializes concurrent booking attempts for the same slot:
        // every concurrent transaction blocks here until this one commits or
        // rolls back, so the overlap check below never races.
        ParkingSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotUnavailableException("Slot not found: " + slotId));

        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new SlotUnavailableException("Slot is currently occupied: " + slotId);
        }

        var overlapping = bookingRepository.findActiveOverlapping(slotId, startTime, endTime);
        if (!overlapping.isEmpty()) {
            throw new OverlappingBookingException("Slot " + slotId + " is already booked for that time window");
        }

        Booking booking = new Booking(user, slot, startTime, endTime);
        slot.setStatus(SlotStatus.RESERVED);
        return bookingRepository.save(booking);
    }

    @Transactional
    public void cancelBooking(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() == BookingStatus.ACTIVE
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Cannot cancel booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
    }

    @Transactional
    public Booking confirmBooking(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Cannot confirm booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(Instant.now());
        return booking;
    }

    @Transactional
    public Booking checkIn(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Cannot check in booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.ACTIVE);
        booking.getSlot().setStatus(SlotStatus.OCCUPIED);
        return booking;
    }

    @Transactional
    public Booking checkOut(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new InvalidBookingStateException("Cannot check out booking in state " + booking.getStatus());
        }
        double predictedOccupancy = predictionCache.get(booking.getSlot().getLocation().getId())
                .map(OccupancyPrediction::predictedOccupancyPct)
                .orElse(0.5);
        var cost = pricingService.calculateCost(booking.getStartTime(), booking.getEndTime(), predictedOccupancy);
        booking.setTotalCost(cost);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
        return booking;
    }

    private Booking getOwnedBooking(String userEmail, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }
        return booking;
    }

    @Transactional
    public int expireStalePendingBookings(Duration pendingExpiry) {
        Instant cutoff = Instant.now().minus(pendingExpiry);
        var stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
        for (Booking booking : stale) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.getSlot().setStatus(SlotStatus.AVAILABLE);
        }
        return stale.size();
    }
}
