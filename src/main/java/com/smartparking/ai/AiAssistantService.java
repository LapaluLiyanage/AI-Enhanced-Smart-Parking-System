package com.smartparking.ai;

import java.util.List;

public interface AiAssistantService {

    OccupancyPrediction predictOccupancy(String locationName, List<HourlyBookingCount> history);

    BookingIntent parseBookingIntent(String message, List<String> knownLocationNames);
}
