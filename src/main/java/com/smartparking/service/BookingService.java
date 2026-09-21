package com.smartparking.service;

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

import java.time.Instant;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }
        if (booking.getStatus() == BookingStatus.ACTIVE
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Cannot cancel booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
    }
}
