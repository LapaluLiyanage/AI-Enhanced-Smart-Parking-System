package com.smartparking.controller;

import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @WithMockUser
    void listsAvailableSlotsForLocation() throws Exception {
        Location location = locationRepository.save(new Location("Mall Parking", "123 Main St", 2));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));
        slotRepository.save(new ParkingSlot(location, 2, SlotStatus.OCCUPIED));

        mockMvc.perform(get("/locations/{id}/slots?available=true", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }
}
