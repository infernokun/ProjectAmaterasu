package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.components.RefreshCookie;
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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /** The refresh cookie set by a response, or "" when it was cleared, or null when none was set. */
    private static String refreshCookie(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(RefreshCookie.NAME + "="))
                .map(header -> header.substring(RefreshCookie.NAME.length() + 1, header.indexOf(';')))
                .findFirst().orElse(null);
    }

    private record Session(String accessToken, String refreshCookie) {
    }

    private Session session(String username, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("username", username, "password", password), null)
                .andExpect(status().isOk()).andReturn();
        String access = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        return new Session(access, refreshCookie(result));
    }

    private ResultActions withRefreshCookie(String url, String cookie) throws Exception {
        var request = post(url);
        if (cookie != null) {
            request.cookie(new Cookie(RefreshCookie.NAME, cookie));
        }
        return mvc.perform(request);
    }

    private boolean isValid(String cookie) throws Exception {
        String body = withRefreshCookie("/api/auth/refresh/validate", cookie)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").asBoolean();
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
    void refreshTokenOnlyTravelsInAnHttpOnlyCookie() throws Exception {
        User user = createUser(Role.MEMBER, null);
        MvcResult result = postJson("/api/auth/login",
                Map.of("username", user.getUsername(), "password", "password123"), null).andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("data").has("refreshToken"))
                .isFalse();
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(RefreshCookie.NAME + "=").contains("HttpOnly", "SameSite=Strict",
                "Path=/api/auth");
        // A token in the body is ignored
        postJson("/api/auth/refresh", Map.of("refreshToken", refreshCookie(result)), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndDetectsReplay() throws Exception {
        User user = createUser(Role.MEMBER, null);
        String first = session(user.getUsername(), "password123").refreshCookie();

        assertThat(refreshTokenRepository.findAll()).noneMatch(token -> token.getToken().equals(first));

        MvcResult rotated = withRefreshCookie("/api/auth/refresh", first).andExpect(status().isOk()).andReturn();
        String second = refreshCookie(rotated);
        assertThat(second).isNotBlank().isNotEqualTo(first);

        // Another tab refreshing with the old cookie inside the grace window gets an access token and
        // keeps the newer cookie
        MvcResult concurrent = withRefreshCookie("/api/auth/refresh", first).andExpect(status().isOk()).andReturn();
        assertThat(refreshCookie(concurrent)).isNull();
        assertThat(objectMapper.readTree(concurrent.getResponse().getContentAsString())
                .get("data").get("accessToken").asText()).isNotBlank();
        assertThat(isValid(second)).isTrue();

        // Outside the grace window a replay means the token leaked: every session is revoked
        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()) && token.isRevoked())
                .forEach(token -> {
                    token.setRevokedAt(Instant.now().minusSeconds(120));
                    refreshTokenRepository.save(token);
                });
        MvcResult replay = withRefreshCookie("/api/auth/refresh", first)
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(refreshCookie(replay)).isEmpty();
        assertThat(isValid(second)).isFalse();
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        User user = createUser(Role.MEMBER, null);
        String first = session(user.getUsername(), "password123").refreshCookie();
        String second = refreshCookie(withRefreshCookie("/api/auth/refresh", first).andReturn());

        MvcResult logout = withRefreshCookie("/api/auth/logout", second).andExpect(status().isOk()).andReturn();
        assertThat(refreshCookie(logout)).isEmpty();
        withRefreshCookie("/api/auth/refresh", second).andExpect(status().isUnauthorized());
        // The grace window does not bring back a session that was signed out
        withRefreshCookie("/api/auth/refresh", first).andExpect(status().isUnauthorized());
        withRefreshCookie("/api/auth/logout", null).andExpect(status().isOk());
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

    @Test
    void changingPasswordSignsOutOtherSessions() throws Exception {
        User user = createUser(Role.MEMBER, null);
        Session first = session(user.getUsername(), "password123");
        String oldAccess = first.accessToken();
        String oldRefresh = first.refreshCookie();
        Thread.sleep(1100); // access tokens carry whole-second timestamps

        postJson("/api/auth/password", Map.of("currentPassword", "wrong-one", "newPassword", "newpassword1"), oldAccess)
                .andExpect(status().isUnauthorized());
        MvcResult changed = postJson("/api/auth/password",
                Map.of("currentPassword", "password123", "newPassword", "newpassword1"), oldAccess)
                .andExpect(status().isOk()).andReturn();
        String newAccess = objectMapper.readTree(changed.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        assertThat(refreshCookie(changed)).isNotBlank();

        mvc.perform(get("/api/labs").header("Authorization", "Bearer " + oldAccess)).andExpect(status().isUnauthorized());
        withRefreshCookie("/api/auth/refresh", oldRefresh).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/labs").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());
        login(user.getUsername(), "newpassword1");
    }

    @Test
    void disabledUsersAreLockedOutImmediately() throws Exception {
        User user = createUser(Role.MEMBER, null);
        Session session = session(user.getUsername(), "password123");
        String access = session.accessToken();
        Thread.sleep(1100);

        String admin = accessToken("test_admin", "test_password");
        mvc.perform(put("/api/user/" + user.getId() + "/disabled")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/labs").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        withRefreshCookie("/api/auth/refresh", session.refreshCookie()).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", user.getUsername(), "password", "password123"), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotDisableThemselves() throws Exception {
        String admin = accessToken("test_admin", "test_password");
        String adminId = userRepository.findByUsernameIgnoreCase("test_admin").orElseThrow().getId();
        mvc.perform(put("/api/user/" + adminId + "/disabled")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void socketHandshakeNeedsAToken() throws Exception {
        mvc.perform(get("/socket-handler/update")).andExpect(status().isUnauthorized());
        int status = mvc.perform(get("/socket-handler/update").param("access_token", memberToken()))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
        // Only the socket accepts a token in the query string
        mvc.perform(get("/api/labs").param("access_token", memberToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void noCrossOriginAccessUnlessConfigured() throws Exception {
        String origin = mvc.perform(options("/api/labs")
                        .header("Origin", "https://elsewhere.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn().getResponse().getHeader("Access-Control-Allow-Origin");
        assertThat(origin).isNull();
    }
}
