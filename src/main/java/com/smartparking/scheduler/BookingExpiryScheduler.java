package com.smartparking.scheduler;

import com.smartparking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;
    private final long pendingExpiryMinutes;

    public BookingExpiryScheduler(
            BookingService bookingService,
            @Value("${app.booking.pending-expiry-minutes}") long pendingExpiryMinutes) {
        this.bookingService = bookingService;
        this.pendingExpiryMinutes = pendingExpiryMinutes;
    }

    @Scheduled(fixedRate = 60_000)
    public void expirePendingBookings() {
        int expired = bookingService.expireStalePendingBookings(Duration.ofMinutes(pendingExpiryMinutes));
        if (expired > 0) {
            log.info("Expired {} stale pending booking(s)", expired);
        }
    }
}
