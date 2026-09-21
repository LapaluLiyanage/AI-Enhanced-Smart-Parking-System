package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.OccupancyPrediction;
import com.smartparking.entity.Location;
import com.smartparking.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @MockBean private AiAssistantService aiAssistantService;

    @Test
    @WithMockUser
    void returnsPredictionFromAiAssistantService() throws Exception {
        Location location = locationRepository.save(new Location("Downtown Lot", "addr", 5));
        when(aiAssistantService.predictOccupancy(anyString(), any(List.class)))
                .thenReturn(new OccupancyPrediction(0.82, "High historical demand at this hour"));

        mockMvc.perform(get("/predictions/{id}", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.predictedOccupancyPct").value(0.82))
            .andExpect(jsonPath("$.reasoning").value("High historical demand at this hour"));
    }
}
