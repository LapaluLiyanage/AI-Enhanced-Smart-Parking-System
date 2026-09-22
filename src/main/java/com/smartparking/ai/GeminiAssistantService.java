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
        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = requestBody.putArray("contents").addObject();
        content.putArray("parts").addObject().put("text", """
                Current UTC time is %s. Known parking location names: %s.
                Parse the user's booking request and call create_booking with
                the closest matching location name from the known list, an ISO-8601
                UTC start time, and a duration in minutes. If no start time is given,
                assume now. If no duration is given, assume 60 minutes.

                User request: "%s"
                """.formatted(java.time.Instant.now(), knownLocationNames, message));

        ObjectNode tool = requestBody.putArray("tools").addObject();
        ObjectNode functionDeclaration = tool.putArray("functionDeclarations").addObject();
        functionDeclaration.put("name", "create_booking");
        functionDeclaration.put("description", "Create a parking booking from parsed intent");
        ObjectNode parameters = functionDeclaration.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("locationName").put("type", "string");
        properties.putObject("startTime").put("type", "string").put("description", "ISO-8601 UTC datetime");
        properties.putObject("durationMinutes").put("type", "integer");
        parameters.putArray("required").add("locationName").add("startTime").add("durationMinutes");

        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        JsonNode functionCall = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("functionCall");

        if (functionCall.isMissingNode()) {
            throw new IllegalArgumentException("Could not understand booking request: " + message);
        }

        JsonNode args = functionCall.path("args");
        String locationName = args.path("locationName").asText();
        java.time.Instant startTime;
        try {
            startTime = java.time.Instant.parse(args.path("startTime").asText());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "AI assistant returned an unparseable start time: " + args.path("startTime").asText());
        }
        int durationMinutes = args.path("durationMinutes").asInt(60);

        return new BookingIntent(locationName, startTime, durationMinutes);
    }
}
