package com.infernokun.amaterasu.controllers.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.GlobalExceptionHandler;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.services.entity.TeamService;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    private MockMvc mockMvc;

    private Team team;

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamController teamController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<Team> teamCaptor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(teamController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        team = Team.builder().name("testTeam").description("AwesomeTeam!!!").build();
        team.setId("1");
    }

    @Test
    void getAllTeams() throws Exception {
        mockMvc.perform(get("/api/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getTeamById() throws Exception {
        mockMvc.perform(get("/api/team/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createTeam() throws Exception {
        mockMvc.perform(post("/api/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(team)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void deleteTeam() throws Exception {
        mockMvc.perform(delete("/api/team/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateTeam() throws Exception {
        mockMvc.perform(put("/api/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(team)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getTeamByIdNotFound() throws Exception {
        when(teamService.findTeamById("2")).thenThrow(new ResourceNotFoundException("Team not found!"));

        mockMvc.perform(get("/api/team/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteTeamNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Team not found!")).when(teamService).deleteTeam("2");

        mockMvc.perform(delete("/api/team/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateTeamNotFound() throws Exception {
        when(teamService.updateTeam(teamCaptor.capture())).thenThrow(new ResourceNotFoundException("Team not found!"));

        mockMvc.perform(put("/api/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(team)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
