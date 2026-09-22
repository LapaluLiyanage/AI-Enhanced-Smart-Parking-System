package com.smartparking.service;

import com.smartparking.entity.Booking;
import com.smartparking.exception.AnomalyDetectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnomalyDetectionService {

    private final int maxBookingsPerWindow;
    private final int windowMinutes;

    public AnomalyDetectionService(
            @Value("${app.anomaly.max-bookings-per-window:5}") int maxBookingsPerWindow,
            @Value("${app.anomaly.window-minutes:5}") int windowMinutes) {
        this.maxBookingsPerWindow = maxBookingsPerWindow;
        this.windowMinutes = windowMinutes;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void checkForAbuse(String userEmail, List<Booking> recentBookingsInWindow) {
        if (recentBookingsInWindow.size() > maxBookingsPerWindow) {
            throw new AnomalyDetectedException(
                    "User " + userEmail + " exceeded " + maxBookingsPerWindow +
                    " bookings in " + windowMinutes + " minutes");
        }
    }
}
