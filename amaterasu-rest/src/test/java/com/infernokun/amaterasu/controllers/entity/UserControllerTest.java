package com.infernokun.amaterasu.controllers.entity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.GlobalExceptionHandler;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.entity.UserService;
import java.util.Collections;
import java.util.List;
import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.entities.alt.CreateUserRequest;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import com.infernokun.amaterasu.services.entity.AuditService;
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

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private AuthenticationService authenticationService;
    @Mock private TeamService teamService;
    @Mock private AuditService auditService;
    @Mock private CurrentUser currentUser;

    @InjectMocks private UserController userController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user;

    @Captor private ArgumentCaptor<User> userCaptor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        user = User.builder().username("testUser").password("password").build();
        user.setId("1");
    }

    @Test
    void getAllUsers() throws Exception {
        List<User> users = Collections.singletonList(user);
        when(userService.findAllUsers()).thenReturn(users); // Return the list

        mockMvc
                .perform(get("/api/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("testUser"));
    }

    @Test
    void getUserById() throws Exception {
        when(userService.findUserById("1")).thenReturn(user);

        mockMvc
                .perform(get("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testUser"));
    }

    @Test
    void createUser() throws Exception {
        when(authenticationService.createUser("testUser", "password123", Role.CREATOR, null)).thenReturn(user);

        mockMvc
                .perform(
                        post("/api/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new CreateUserRequest("testUser", "password123", Role.CREATOR, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.username").value("testUser"));
    }

    @Test
    void createManyUsers() throws Exception {
        List<User> users = Collections.singletonList(user);
        when(userService.createManyUsers(any())).thenReturn(users);

        mockMvc
                .perform(
                        post("/api/user/many")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(users)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data[0].username").value("testUser"));
    }

    @Test
    void deleteUser() throws Exception {
        when(userService.deleteUser("1")).thenReturn(user);

        mockMvc
                .perform(delete("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testUser"));

    }

    @Test
    void updateUser() throws Exception {
        when(userService.updateUser(any(String.class), userCaptor.capture())).thenReturn(user);

        mockMvc
                .perform(
                        put("/api/user/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testUser"));
    }

    @Test
    void updateUserTeam() throws Exception {
        when(userService.updateUserTeam("1", "1")).thenReturn(user);

        mockMvc
                .perform(put("/api/user/team").param("userId", "1").param("teamId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testUser"));
    }

    @Test
    void changeRole() throws Exception {
        when(currentUser.id()).thenReturn("admin");
        when(authenticationService.changeRole("1", Role.FACILITATOR, "admin")).thenReturn(user);

        mockMvc
                .perform(put("/api/user/1/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FACILITATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testUser"));
    }

    @Test
    void updateUserTeamWithoutATeamTakesThemOff() throws Exception {
        when(userService.updateUserTeam("1", null)).thenReturn(user);

        mockMvc
                .perform(put("/api/user/team").param("userId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void updateUserNotFound() throws Exception {
        when(userService.updateUser(any(String.class), any(User.class)))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(put("/api/user/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteUserNotFound() throws Exception {
        when(userService.deleteUser("2"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc
                .perform(delete("/api/user/2"))
                .andExpect(status().isNotFound()) // Expect NOT_FOUND
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
