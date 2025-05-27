package com.infernokun.amaterasu.controllers.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.controllers.entity.lab.RemoteServerStatsController;
import com.infernokun.amaterasu.exceptions.GlobalExceptionHandler;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.services.entity.RemoteServerStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RemoteServerStatsControllerTest {

    private MockMvc mockMvc;

    private RemoteServerStats remoteServerStats;

    @Mock
    private RemoteServerStatsService remoteServerStatsService;

    @InjectMocks
    private RemoteServerStatsController remoteServerStatsController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(remoteServerStatsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        RemoteServer remoteServer = new RemoteServer();
        remoteServer.setId("1");

        remoteServerStats = RemoteServerStats.builder()
                .hostname("test-server")
                .osName("Linux")
                .osVersion("Ubuntu 20.04")
                .totalRam(16.0f)
                .availableRam(8.0f)
                .usedRam(8.0f)
                .cpu(8)
                .cpuUsagePercent(25.5)
                .totalDiskSpace(500.0f)
                .availableDiskSpace(200.0f)
                .usedDiskSpace(300.0f)
                .uptime(3600L)
                .status(LabStatus.ACTIVE)
                .remoteServer(remoteServer)
                .build();
        remoteServerStats.setId("1");
    }


    @Test
    void getAllStats() throws Exception {
        mockMvc.perform(get("/api/remote-server-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getStatsById() throws Exception {
        mockMvc.perform(get("/api/remote-server-stats/1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void createStats() throws Exception {
        mockMvc.perform(post("/api/remote-server-stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(remoteServerStats)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void deleteStats() throws Exception {
        mockMvc.perform(delete("/api/remote-server-stats/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateStats() throws Exception {
        mockMvc.perform(put("/api/remote-server-stats/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(remoteServerStats)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getStatsByIdNotFound() throws Exception {
        when(remoteServerStatsService.findStatsById("2")).thenThrow(new ResourceNotFoundException("Stats not found!"));

        mockMvc.perform(get("/api/remote-server-stats/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteStatsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Stats not found!"))
                .when(remoteServerStatsService).deleteStats("2");

        mockMvc.perform(delete("/api/remote-server-stats/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateStatsNotFound() throws Exception {
        when(remoteServerStatsService.updateStats(any(RemoteServerStats.class)))
                .thenThrow(new ResourceNotFoundException("Stats not found!"));

        mockMvc.perform(put("/api/remote-server-stats/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(remoteServerStats)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
