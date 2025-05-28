package com.infernokun.amaterasu.controllers.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.controllers.entity.lab.LabController;
import com.infernokun.amaterasu.exceptions.GlobalExceptionHandler;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.LabRequest;
import com.infernokun.amaterasu.models.dto.LabDTO;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.entity.lab.LabService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LabControllerTest {

    private MockMvc mockMvc;

    private Lab lab;
    private LabDTO labDTO;
    private LabRequest labRequest;
    private LabActionResult labActionResult;
    private LabTracker labTracker;
    private RemoteServer remoteServer;

    @Mock
    private LabService labService;

    @Mock
    private LabFileUploadService labFileUploadService;

    @Mock
    private RemoteServerService remoteServerService;

    @InjectMocks
    private LabController labController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<Lab> labCaptor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(labController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        remoteServer = new RemoteServer();
        remoteServer.setId("rs1");

        lab = Lab.builder()
                .capacity(1)
                .description("test")
                .labType(LabType.DOCKER_COMPOSE)
                .name("test")
                .ready(true)
                .version("1")
                .dockerFile("test")
                .build();
        lab.setId("1");

        labDTO = new LabDTO();

        labRequest = new LabRequest();
        labRequest.setLabId("1");
        labRequest.setRemoteServerId("rs1");
        labRequest.setUserId("user1");
        labRequest.setLabTrackerId("lt1");

        labActionResult = new LabActionResult();

        labTracker = new LabTracker();
        labTracker.setId("lt1");
    }

    @Test
    void getAllLabs() throws Exception {
        when(labService.findAllLabs()).thenReturn(Collections.singletonList(lab));

        mockMvc.perform(get("/api/labs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void getLabById() throws Exception {
        when(labService.findLabById("1")).thenReturn(lab);

        mockMvc.perform(get("/api/labs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Lab retrieved successfully."));
    }

    @Test
    void getLabByIdNotFound() throws Exception {
        when(labService.findLabById("2")).thenThrow(new ResourceNotFoundException("Lab not found!"));

        mockMvc.perform(get("/api/labs/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void checkLabReadiness() throws Exception {
        // Assume labService.checkDockerComposeValidity returns true.
        when(remoteServerService.findServerById("rs1")).thenReturn(remoteServer);
        when(labService.checkDockerComposeValidity("1", remoteServer)).thenReturn(true);

        mockMvc.perform(get("/api/labs/check/1/rs1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void createLab() throws Exception {
        when(remoteServerService.findServerById("rs1")).thenReturn(remoteServer);
        when(labService.createLab(labDTO, remoteServer)).thenReturn(lab);

        mockMvc.perform(post("/api/labs")
                        .param("remoteServerId", "rs1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void createManyLabs() throws Exception {
        when(labService.createManyLabs(any())).thenReturn(Collections.singletonList(lab));

        mockMvc.perform(post("/api/labs/many")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singletonList(lab))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void updateLab() throws Exception {
        when(labService.updateLab(any(Lab.class))).thenReturn(lab);

        mockMvc.perform(put("/api/labs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lab)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateLabNotFound() throws Exception {
        when(labService.updateLab(any(Lab.class))).thenThrow(new ResourceNotFoundException("Lab not found!"));

        mockMvc.perform(put("/api/labs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lab)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void uploadLabFile() throws Exception {
        String content = "file-content";
        when(remoteServerService.findServerById("rs1")).thenReturn(remoteServer);
        when(labService.uploadLabFile("1", content, remoteServer)).thenReturn("upload-success");

        mockMvc.perform(post("/api/labs/upload/1/rs1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("upload-success"));
    }

    @Test
    void validateDockerComposeYml() throws Exception {
        String content = "docker-compose content";
        when(labFileUploadService.validateDockerComposeFile(content)).thenReturn(true);

        mockMvc.perform(post("/api/labs/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void stopLab() throws Exception {
        when(remoteServerService.findServerById("rs1")).thenReturn(remoteServer);
        when(labService.stopLab("1", "user1", "lt1", remoteServer)).thenReturn(labActionResult);

        mockMvc.perform(post("/api/labs/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteLab() throws Exception {
        when(remoteServerService.findServerById("rs1")).thenReturn(remoteServer);
        when(labService.deleteLab("1", "user1", "lt1", remoteServer)).thenReturn(labActionResult);

        mockMvc.perform(post("/api/labs/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteLabFromTeam_Success() throws Exception {
        when(labService.deleteLabFromTeam("1", "user1", "lt1")).thenReturn(labTracker);

        mockMvc.perform(post("/api/labs/delete-lab-from-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("lt1"));
    }

    @Test
    void deleteLabFromTeam_Failure() throws Exception {
        when(labService.deleteLabFromTeam("1", "user1", "lt1"))
                .thenThrow(new ResourceNotFoundException());

        mockMvc.perform(post("/api/labs/delete-lab-from-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void clearLabs() throws Exception {
        // For the /dev endpoint, which returns void, we only check status.
        mockMvc.perform(post("/api/labs/dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isOk());
    }
}
