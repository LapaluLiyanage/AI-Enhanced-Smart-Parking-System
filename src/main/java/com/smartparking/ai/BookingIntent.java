package com.smartparking.ai;

import java.time.Instant;

public record BookingIntent(String locationName, Instant startTime, int durationMinutes) {
}
