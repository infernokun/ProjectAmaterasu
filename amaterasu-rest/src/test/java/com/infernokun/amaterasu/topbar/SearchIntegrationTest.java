package com.infernokun.amaterasu.topbar;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The top bar search returns only what the caller's role and team may open. */
@SpringBootTest
@AutoConfigureMockMvc
class SearchIntegrationTest extends ApiTestSupport {
    @Autowired private RoomRepository roomRepository;
    @Autowired private CTFEntityRepository ctfEntityRepository;
    @Autowired private LabRepository labRepository;
    @Autowired private LabTrackerRepository labTrackerRepository;
    @Autowired private RemoteServerRepository remoteServerRepository;

    private String tag;
    private User redMember;
    private User blueMember;
    private User author;
    private Team red;
    private LabTracker redTracker;
    private LabTracker blueTracker;
    private Lab draft;
    private CTFEntity released;
    private CTFEntity later;
    private CTFEntity otherRoom;
    private RemoteServer server;

    @BeforeEach
    void setUp() throws Exception {
        tag = unique("zq").toLowerCase();
        red = team(tag + "red");
        Team blue = team(tag + "blue");
        redMember = user(Role.MEMBER, red);
        blueMember = user(Role.MEMBER, blue);
        author = user(Role.CREATOR, null);

        Lab ready = labRepository.save(Lab.builder().name(tag + " Web Range").labType(LabType.DOCKER_COMPOSE).ready(true).build());
        Lab vmLab = labRepository.save(Lab.builder().name(tag + " Forest").labType(LabType.VIRTUAL_MACHINE).ready(true).build());
        draft = labRepository.save(Lab.builder().name(tag + " Draft Lab").labType(LabType.DOCKER_COMPOSE).ready(false).build());
        redTracker = labTrackerRepository.save(LabTracker.builder().labStarted(ready).labOwner(red).labStatus(LabStatus.ACTIVE).build());
        blueTracker = labTrackerRepository.save(LabTracker.builder().labStarted(vmLab).labOwner(blue).labStatus(LabStatus.ACTIVE).build());

        Room joined = roomRepository.save(Room.builder().name(tag + " Winter Range").build());
        Room notJoined = roomRepository.save(Room.builder().name(tag + " Summer Range").build());
        released = challenge(joined, tag + " Header Hunt", "AMA{secret-flag}", LocalDateTime.now().minusHours(1));
        later = challenge(joined, tag + " Golden Ticket", "AMA{later}", LocalDateTime.now().plusHours(3));
        otherRoom = challenge(notJoined, tag + " Padding Oracle", "AMA{other}", LocalDateTime.now().minusHours(1));
        postAs("/api/room/join/" + joined.getId() + "/" + redMember.getId(), token(redMember), Map.of()).andExpect(status().isOk());

        server = remoteServerRepository.save(RemoteServer.builder().name(tag + "-dock").ipAddress("10.9.9.9")
                .serverType(ServerType.DOCKER_HOST).build());
    }

    private CTFEntity challenge(Room room, String title, String flag, LocalDateTime release) {
        CTFEntity challenge = new CTFEntity();
        challenge.setQuestion(title);
        challenge.setCategory("Web");
        challenge.setPoints(200);
        challenge.setRoom(room);
        challenge.setReleaseDate(release);
        Flag answer = new Flag();
        answer.setFlag(flag);
        answer.setCtfEntity(challenge);
        challenge.setFlags(new ArrayList<>(List.of(answer)));
        return ctfEntityRepository.save(challenge);
    }

    private JsonNode search(String token) throws Exception {
        return data(getAs("/api/search?q=" + tag + "&limit=20", token).andExpect(status().isOk())).get("hits");
    }

    private static List<String> ids(JsonNode hits, String type) {
        List<String> ids = new ArrayList<>();
        hits.forEach(hit -> {
            if (hit.get("type").asText().equals(type)) {
                ids.add(hit.get("id").asText());
            }
        });
        return ids;
    }

    @Test
    void searchNeedsASignedInUser() throws Exception {
        mvc.perform(get("/api/search?q=" + tag)).andExpect(status().isUnauthorized());
    }

    @Test
    void shortQueriesFindNothing() throws Exception {
        assertThat(data(getAs("/api/search?q=z", token(redMember)).andExpect(status().isOk())).get("hits")).isEmpty();
    }

    @Test
    void membersFindTheirTeamLabsTheCatalogRoomsAndReleasedChallengesInJoinedRooms() throws Exception {
        String raw = getAs("/api/search?q=" + tag + "&limit=20", token(redMember)).andReturn().getResponse().getContentAsString();
        JsonNode hits = search(token(redMember));

        assertThat(ids(hits, "TEAM_LAB")).containsExactly(redTracker.getId());
        assertThat(ids(hits, "LAB")).hasSize(2).doesNotContain(draft.getId());
        assertThat(ids(hits, "ROOM")).hasSize(2);
        assertThat(ids(hits, "CHALLENGE")).containsExactly(released.getId());
        assertThat(ids(hits, "USER")).isEmpty();
        assertThat(ids(hits, "TEAM")).isEmpty();
        assertThat(ids(hits, "SERVER")).isEmpty();
        assertThat(raw).doesNotContain("AMA{");
    }

    @Test
    void anotherTeamDoesNotSeeThisTeamsLabsOrChallengesOfRoomsItDidNotJoin() throws Exception {
        JsonNode hits = search(token(blueMember));
        assertThat(ids(hits, "TEAM_LAB")).containsExactly(blueTracker.getId());
        assertThat(ids(hits, "CHALLENGE")).isEmpty();
    }

    @Test
    void authorsFindEveryLabRoomAndChallenge() throws Exception {
        JsonNode hits = search(token(author));
        assertThat(ids(hits, "LAB")).contains(draft.getId()).hasSize(3);
        assertThat(ids(hits, "CHALLENGE")).containsExactlyInAnyOrder(released.getId(), later.getId(), otherRoom.getId());
        assertThat(ids(hits, "USER")).isEmpty();
        assertThat(ids(hits, "SERVER")).isEmpty();
    }

    @Test
    void adminsAlsoFindUsersTeamsAndServers() throws Exception {
        JsonNode hits = data(getAs("/api/search?q=" + tag + "&limit=20", adminToken()).andExpect(status().isOk())).get("hits");
        assertThat(ids(hits, "TEAM")).hasSize(2).contains(red.getId());
        assertThat(ids(hits, "SERVER")).containsExactly(server.getId());

        JsonNode users = data(getAs("/api/search?q=" + redMember.getUsername(), adminToken())).get("hits");
        assertThat(ids(users, "USER")).containsExactly(redMember.getId());
    }

    @Test
    void wildcardsInTheQueryAreTakenLiterally() throws Exception {
        assertThat(data(getAs("/api/search?q=%25%25", adminToken()).andExpect(status().isOk())).get("hits")).isEmpty();
    }
}
