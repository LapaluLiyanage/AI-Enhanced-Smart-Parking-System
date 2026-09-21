package com.smartparking.dto;

import com.smartparking.entity.Booking;
import com.smartparking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id, Long slotId, Long locationId, Instant startTime, Instant endTime,
        BookingStatus status, BigDecimal totalCost) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getSlot().getLocation().getId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getTotalCost());
    }
}
