package com.smartparking.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GeminiAssistantService implements AiAssistantService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiAssistantService(
            @Value("${app.ai.gemini.base-url}") String baseUrl,
            @Value("${app.ai.gemini.api-key}") String apiKey,
            @Value("${app.ai.gemini.model}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public OccupancyPrediction predictOccupancy(String locationName, List<HourlyBookingCount> history) {
        String historyText = history.stream()
                .map(h -> "day=%d hour=%d bookings=%d".formatted(h.dayOfWeek(), h.hourOfDay(), h.bookingCount()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a parking demand forecaster. Given this historical hourly booking
                count data for parking location "%s" (day 1=Monday..7=Sunday), predict the
                expected occupancy percentage for the NEXT hour. Reply with ONLY a JSON
                object matching exactly: {"predictedOccupancyPct": <number 0.0-1.0>, "reasoning": "<one sentence>"}

                Historical data:
                %s
                """.formatted(locationName, historyText);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = requestBody.putArray("contents").addObject();
        content.putArray("parts").addObject().put("text", prompt);
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        String text = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();

        try {
            JsonNode parsed = objectMapper.readTree(text);
            return new OccupancyPrediction(
                    parsed.path("predictedOccupancyPct").asDouble(0.5),
                    parsed.path("reasoning").asText(""));
        } catch (Exception e) {
            return new OccupancyPrediction(0.5, "Fallback: could not parse model response");
        }
    }

    @Override
    public BookingIntent parseBookingIntent(String message, List<String> knownLocationNames) {
        throw new UnsupportedOperationException("Implemented in Task 9");
    }
}
