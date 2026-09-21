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
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsOccupancyStatsForAdmin() throws Exception {
        Location location = locationRepository.save(new Location("Stat Lot", "addr", 2));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));
        slotRepository.save(new ParkingSlot(location, 2, SlotStatus.OCCUPIED));

        mockMvc.perform(get("/admin/locations/{id}/occupancy", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(1))
            .andExpect(jsonPath("$.occupied").value(1))
            .andExpect(jsonPath("$.occupancyPct").value(0.5));
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsNonAdminUser() throws Exception {
        Location location = locationRepository.save(new Location("Stat Lot 2", "addr", 1));

        mockMvc.perform(get("/admin/locations/{id}/occupancy", location.getId()))
            .andExpect(status().isForbidden());
    }
}
