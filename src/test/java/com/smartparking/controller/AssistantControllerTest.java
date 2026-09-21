package com.smartparking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.BookingIntent;
import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.Role;
import com.smartparking.entity.SlotStatus;
import com.smartparking.entity.User;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private UserRepository userRepository;
    @MockBean private AiAssistantService aiAssistantService;

    @Test
    @WithMockUser(username = "assistant-test@example.com")
    void parsesNaturalLanguageAndCreatesBooking() throws Exception {
        Location location = locationRepository.save(new Location("Mall Entrance", "addr", 1));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));
        userRepository.save(new User("assistant-test@example.com", "hash", Role.USER));

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        when(aiAssistantService.parseBookingIntent(anyString(), anyList()))
                .thenReturn(new BookingIntent("Mall Entrance", start, 120));

        mockMvc.perform(post("/assistant/book")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Object() {
                    public String message = "book me a spot near the mall entrance for 2 hours starting at 3pm";
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationId").value(location.getId()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
