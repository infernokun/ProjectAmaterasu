package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.RefreshTokenRepository;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private LabRepository labRepository;
    @Autowired private LabTrackerRepository labTrackerRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions postJson(String url, Object body, String token) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private JsonNode login(String username, String password) throws Exception {
        String body = postJson("/api/auth/login", Map.of("username", username, "password", password), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private String accessToken(String username, String password) throws Exception {
        return login(username, password).get("accessToken").asText();
    }

    private User createUser(Role role, Team team) {
        User user = new User(unique("u"), passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setTeam(team);
        return userRepository.save(user);
    }

    private String memberToken() throws Exception {
        String username = unique("member");
        postJson("/api/auth/register", Map.of("username", username, "password", "password123"), null)
                .andExpect(status().isOk());
        return accessToken(username, "password123");
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mvc.perform(get("/api/application-info")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresToken() throws Exception {
        mvc.perform(get("/api/labs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/remote-server")).andExpect(status().isUnauthorized());
        postJson("/api/labs/start", Map.of("labId", "x"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokensNotSignedByTheApi() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).generate();
        String forged = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)))
                .encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                        .issuer("amaterasu")
                        .subject("someone")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("roles", "ADMIN")
                        .build()))
                .getTokenValue();

        mvc.perform(get("/api/user").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());

        String member = memberToken();
        String[] parts = member.split("\\.");
        String elevated = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("MEMBER", "ADMIN").getBytes())
                + "." + parts[2];
        mvc.perform(get("/api/user").header("Authorization", "Bearer " + elevated)).andExpect(status().isUnauthorized());
    }

    @Test
    void membersCannotUseAdminOrAuthorEndpoints() throws Exception {
        String member = memberToken();

        mvc.perform(get("/api/labs").header("Authorization", "Bearer " + member)).andExpect(status().isOk());
        mvc.perform(get("/api/user").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        postJson("/api/remote-server", Map.of("name", "x"), member).andExpect(status().isForbidden());
        postJson("/api/team", Map.of("name", "x"), member).andExpect(status().isForbidden());
        postJson("/api/labs", Map.of("name", "x"), member).andExpect(status().isForbidden());
        mvc.perform(get("/api/ctf-entity/full").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ctf-entity/some-id").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
    }

    @Test
    void adminAndAuthorRolesComeFromTheToken() throws Exception {
        String admin = accessToken("test_admin", "test_password");
        mvc.perform(get("/api/user").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        User creator = createUser(Role.CREATOR, null);
        String creatorToken = accessToken(creator.getUsername(), "password123");
        mvc.perform(get("/api/ctf-entity/full").header("Authorization", "Bearer " + creatorToken)).andExpect(status().isOk());
        mvc.perform(get("/api/user").header("Authorization", "Bearer " + creatorToken)).andExpect(status().isForbidden());
    }

    @Test
    void actuatorOnlyExposesHealth() throws Exception {
        String admin = accessToken("test_admin", "test_password");
        // Public, though DOWN (503) here because the tests run without Redis
        int health = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(health).isIn(200, 503);
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump").header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
    }

    @Test
    void registrationAlwaysCreatesMembersAndValidatesInput() throws Exception {
        String username = unique("reg");
        postJson("/api/auth/register", Map.of("username", username, "password", "password123", "role", "ADMIN"), null)
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsernameIgnoreCase(username).orElseThrow().getRole()).isEqualTo(Role.MEMBER);

        postJson("/api/auth/register", Map.of("username", unique("short"), "password", "short"), null)
                .andExpect(status().isBadRequest());
        postJson("/api/auth/register", Map.of("username", "bad name!", "password", "password123"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesAndDetectsReplay() throws Exception {
        User user = createUser(Role.MEMBER, null);
        String first = login(user.getUsername(), "password123").get("refreshToken").asText();

        assertThat(refreshTokenRepository.findAll()).noneMatch(token -> token.getToken().equals(first));

        String second = objectMapper.readTree(postJson("/api/auth/refresh", Map.of("refreshToken", first), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);

        // Replaying the first token inside the grace window fails but leaves the session alone
        postJson("/api/auth/refresh", Map.of("refreshToken", first), null).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh/validate", Map.of("refreshToken", second), null).andExpect(status().isOk());
        assertThat(isValid(second)).isTrue();

        // Outside the grace window a replay means the token leaked: every session is revoked
        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()) && token.isRevoked())
                .forEach(token -> {
                    token.setRevokedAt(Instant.now().minusSeconds(120));
                    refreshTokenRepository.save(token);
                });
        postJson("/api/auth/refresh", Map.of("refreshToken", first), null).andExpect(status().isUnauthorized());
        assertThat(isValid(second)).isFalse();
    }

    private boolean isValid(String refreshToken) throws Exception {
        String body = postJson("/api/auth/refresh/validate", Map.of("refreshToken", refreshToken), null)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").asBoolean();
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        User user = createUser(Role.MEMBER, null);
        String refresh = login(user.getUsername(), "password123").get("refreshToken").asText();
        postJson("/api/auth/logout", Map.of("refreshToken", refresh), null).andExpect(status().isOk());
        postJson("/api/auth/refresh", Map.of("refreshToken", refresh), null).andExpect(status().isUnauthorized());
        postJson("/api/auth/logout", Map.of("refreshToken", "unknown"), null).andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsLockTheAccount() throws Exception {
        User user = createUser(Role.MEMBER, null);
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", Map.of("username", user.getUsername(), "password", "wrong-password"), null)
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/login", Map.of("username", user.getUsername(), "password", "password123"), null)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() throws Exception {
        String unknown = postJson("/api/auth/login", Map.of("username", unique("nobody"), "password", "password123"), null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        User user = createUser(Role.MEMBER, null);
        String wrong = postJson("/api/auth/login", Map.of("username", user.getUsername(), "password", "nope-nope"), null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unknown).get("message")).isEqualTo(objectMapper.readTree(wrong).get("message"));
    }

    @Test
    void membersCanOnlyActOnTheirOwnTeamsLabs() throws Exception {
        Team red = teamRepository.save(Team.builder().name(unique("red")).build());
        Team blue = teamRepository.save(Team.builder().name(unique("blue")).build());
        User redMember = createUser(Role.MEMBER, red);
        createUser(Role.MEMBER, blue);

        Lab lab = labRepository.save(Lab.builder().name(unique("lab")).labType(LabType.DOCKER_COMPOSE)
                .dockerFile("lab.yml").ready(true).build());
        LabTracker blueTracker = labTrackerRepository.save(LabTracker.builder()
                .labStarted(lab).labOwner(blue).labStatus(LabStatus.ACTIVE).build());

        String token = accessToken(redMember.getUsername(), "password123");
        Map<String, String> action = Map.of("labId", lab.getId(), "labTrackerId", blueTracker.getId(), "userId", "spoofed");

        postJson("/api/labs/pause", action, token).andExpect(status().isForbidden());
        postJson("/api/labs/stop", action, token).andExpect(status().isForbidden());
        postJson("/api/labs/delete", action, token).andExpect(status().isForbidden());
        mvc.perform(get("/api/lab-tracker/" + blueTracker.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String trackers = mvc.perform(get("/api/lab-tracker").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(trackers).doesNotContain(blueTracker.getId());
    }

    @Test
    void usersCannotJoinRoomsAsSomeoneElse() throws Exception {
        String member = memberToken();
        User other = createUser(Role.MEMBER, null);
        mvc.perform(post("/api/room/join/some-room/" + other.getId()).header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/room/add-points/some-room/" + other.getId()).header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
    }
}
