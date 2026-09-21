package com.smartparking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull Long slotId,
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime) {
}
