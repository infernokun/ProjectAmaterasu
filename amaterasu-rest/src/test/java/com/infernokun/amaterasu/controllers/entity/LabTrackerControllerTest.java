package com.infernokun.amaterasu.controllers.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.controllers.entity.lab.LabTrackerController;
import com.infernokun.amaterasu.exceptions.GlobalExceptionHandler;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LabTrackerControllerTest {

    private MockMvc mockMvc;

    private LabTracker labTracker;

    @Mock
    private LabTrackerService labTrackerService;

    @InjectMocks
    private LabTrackerController labTrackerController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<LabTracker> labTrackerCaptor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(labTrackerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        labTracker = LabTracker.builder()
                .labStarted(new Lab())
                .labOwner(new Team())
                .labStatus(LabStatus.ACTIVE)
                .remoteServer(new RemoteServer())
                .build();
        labTracker.setId("1");
    }

    @Test
    void getAllLabTrackers() throws Exception {
        when(labTrackerService.findAllLabTrackers()).thenReturn(Collections.singletonList(labTracker));

        mockMvc.perform(get("/api/lab-tracker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void getLabTrackerById() throws Exception {
        // Update the mock to return an Optional
        when(labTrackerService.findLabTrackerById("1")).thenReturn(Optional.of(labTracker));

        mockMvc.perform(get("/api/lab-tracker/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // You can check the custom message too:
                .andExpect(jsonPath("$.message").value("Lab tracker 1 retrieved successfully."));
    }

    @Test
    void getLabTrackerByIdNotFound() throws Exception {
        when(labTrackerService.findLabTrackerById("2"))
                .thenThrow(new ResourceNotFoundException("Lab tracker not found!"));

        mockMvc.perform(get("/api/lab-tracker/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void createLabTracker() throws Exception {
        when(labTrackerService.createLabTracker(any(LabTracker.class))).thenReturn(labTracker);

        mockMvc.perform(post("/api/lab-tracker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labTracker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void createManyLabTrackers() throws Exception {
        List<LabTracker> trackers = Collections.singletonList(labTracker);
        when(labTrackerService.createManyLabTrackers(any())).thenReturn(trackers);

        mockMvc.perform(post("/api/lab-tracker/many")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trackers)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void deleteLabTracker() throws Exception {
        when(labTrackerService.deleteLabTracker("1")).thenReturn(labTracker);

        mockMvc.perform(delete("/api/lab-tracker/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteLabTrackerNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Lab tracker not found!"))
                .when(labTrackerService).deleteLabTracker("2");

        mockMvc.perform(delete("/api/lab-tracker/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateLabTracker() throws Exception {
        when(labTrackerService.updateLabTracker(any(LabTracker.class))).thenReturn(labTracker);

        mockMvc.perform(put("/api/lab-tracker/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labTracker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateLabTrackerNotFound() throws Exception {
        when(labTrackerService.updateLabTracker(any(LabTracker.class)))
                .thenThrow(new ResourceNotFoundException("Lab tracker not found!"));

        mockMvc.perform(put("/api/lab-tracker/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labTracker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
