package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Room facilitators, who may open a room's live view and award points in it, and challenge order. */
@SpringBootTest
@AutoConfigureMockMvc
class RoomStaffIntegrationTest extends IntegrationSupport {

    @Test
    void facilitatorsOnlyRunTheRoomsTheyFacilitate() throws Exception {
        User facilitator = user(Role.FACILITATOR, null);
        Room theirs = room(facilitator.getId());
        Room other = room();
        String token = token(facilitator);

        call(get("/api/room/" + theirs.getId() + "/live"), token).andExpect(status().isOk());
        call(get("/api/room/" + other.getId() + "/live"), token).andExpect(status().isForbidden());

        String admin = admin();
        call(get("/api/room/" + other.getId() + "/live"), admin).andExpect(status().isOk());

        JsonNode facilitated = data(call(get("/api/room/facilitated"), token).andExpect(status().isOk()).andReturn());
        List<String> ids = new ArrayList<>();
        facilitated.forEach(room -> ids.add(room.get("id").asText()));
        assertThat(ids).contains(theirs.getId()).doesNotContain(other.getId());

        JsonNode forAdmin = data(call(get("/api/room/facilitated"), admin).andExpect(status().isOk()).andReturn());
        List<String> adminIds = new ArrayList<>();
        forAdmin.forEach(room -> adminIds.add(room.get("id").asText()));
        assertThat(adminIds).contains(theirs.getId(), other.getId());
    }

    @Test
    void facilitatorsOnlyAwardPointsInTheirRooms() throws Exception {
        User facilitator = user(Role.FACILITATOR, null);
        Team team = team();
        User player = user(Role.MEMBER, team);
        Room theirs = room(facilitator.getId());
        Room other = room();
        join(player, theirs);
        join(player, other);
        String token = token(facilitator);
        Map<String, Object> award = Map.of("points", 25, "reason", "Good writeup");

        send(post("/api/room/add-points/" + other.getId() + "/" + player.getId()), award, token)
                .andExpect(status().isForbidden());
        // Refused before the request is even checked, so nothing about the room leaks
        send(post("/api/room/add-points/" + other.getId() + "/" + player.getId()), Map.of("points", 0, "reason", ""), token)
                .andExpect(status().isForbidden());
        send(post("/api/room/add-points/" + theirs.getId() + "/" + player.getId()), award, token)
                .andExpect(status().isOk());
        send(post("/api/room/add-points/" + other.getId() + "/" + player.getId()), award, admin())
                .andExpect(status().isOk());
    }

    @Test
    void membersAndCreatorsCannotOpenTheLiveViewOrAward() throws Exception {
        User member = user(Role.MEMBER, team());
        User creator = user(Role.CREATOR, null);
        Room room = room(member.getId(), creator.getId());
        join(member, room);
        for (String token : List.of(token(member), token(creator))) {
            call(get("/api/room/" + room.getId() + "/live"), token).andExpect(status().isForbidden());
            call(get("/api/room/facilitated"), token).andExpect(status().isForbidden());
            send(post("/api/room/add-points/" + room.getId() + "/" + member.getId()),
                    Map.of("points", 5, "reason", "Me"), token).andExpect(status().isForbidden());
        }
    }

    @Test
    void authorsAssignFacilitatorsWhoMustHaveTheRole() throws Exception {
        User facilitator = user(Role.FACILITATOR, null);
        User member = user(Role.MEMBER, null);
        User creator = user(Role.CREATOR, null);
        Room room = room();
        String author = token(creator);

        JsonNode candidates = data(call(get("/api/room/facilitator-candidates"), author).andExpect(status().isOk()).andReturn());
        List<String> names = new ArrayList<>();
        candidates.forEach(person -> names.add(person.get("username").asText()));
        assertThat(names).contains(facilitator.getUsername()).doesNotContain(member.getUsername(), creator.getUsername());

        send(put("/api/room/" + room.getId() + "/facilitators"), Map.of("userIds", List.of(member.getId())), author)
                .andExpect(status().isBadRequest());
        JsonNode saved = data(send(put("/api/room/" + room.getId() + "/facilitators"),
                Map.of("userIds", List.of(facilitator.getId(), facilitator.getId())), author)
                .andExpect(status().isOk()).andReturn());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).get("username").asText()).isEqualTo(facilitator.getUsername());
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getFacilitators()).containsExactly(facilitator.getId());
        assertThat(events("ROOM_FACILITATORS_CHANGED", room.getId())).hasSize(1);

        JsonNode listed = data(call(get("/api/room/" + room.getId() + "/facilitators"), author).andExpect(status().isOk()).andReturn());
        assertThat(listed.get(0).get("id").asText()).isEqualTo(facilitator.getId());

        // Assigned now, so the live view opens for them
        call(get("/api/room/" + room.getId() + "/live"), token(facilitator)).andExpect(status().isOk());

        // The facilitator list is not part of the public room JSON
        JsonNode publicRoom = data(call(get("/api/room/" + room.getId()), token(member)).andExpect(status().isOk()).andReturn());
        assertThat(publicRoom.has("facilitators")).isFalse();
    }

    @Test
    void membersAndFacilitatorsCannotChangeFacilitatorsOrOrder() throws Exception {
        User member = user(Role.MEMBER, null);
        User facilitator = user(Role.FACILITATOR, null);
        Room room = room(facilitator.getId());
        CTFEntity a = challenge(room, "A", null, 0);
        for (String token : List.of(token(member), token(facilitator))) {
            call(get("/api/room/facilitator-candidates"), token).andExpect(status().isForbidden());
            call(get("/api/room/" + room.getId() + "/facilitators"), token).andExpect(status().isForbidden());
            send(put("/api/room/" + room.getId() + "/facilitators"), Map.of("userIds", List.of()), token)
                    .andExpect(status().isForbidden());
            send(put("/api/room/" + room.getId() + "/order"), Map.of("challengeIds", List.of(a.getId())), token)
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void authorsReorderChallengesAndRoomsListThemInThatOrder() throws Exception {
        Room room = room();
        User player = user(Role.MEMBER, team());
        join(player, room);
        CTFEntity first = challenge(room, "Zulu", null, 0);
        CTFEntity second = challenge(room, "Alpha", null, 1);
        CTFEntity third = challenge(room, "Mike", null, 2);
        String author = token(user(Role.CREATOR, null));

        send(put("/api/room/" + room.getId() + "/order"), Map.of("challengeIds", List.of(third.getId(), first.getId())), author)
                .andExpect(status().isBadRequest());
        send(put("/api/room/" + room.getId() + "/order"),
                Map.of("challengeIds", List.of(third.getId(), first.getId(), second.getId())), author)
                .andExpect(status().isOk());
        assertThat(events("CHALLENGES_REORDERED", room.getId())).hasSize(1);

        JsonNode authored = data(call(get("/api/ctf-entity/room/" + room.getId()), author).andExpect(status().isOk()).andReturn());
        assertThat(titles(authored, "question")).containsExactly("Mike", "Zulu", "Alpha");

        JsonNode overview = data(call(get("/api/room/" + room.getId() + "/overview"), token(player))
                .andExpect(status().isOk()).andReturn()).get("challenges");
        assertThat(titles(overview, "title")).containsExactly("Mike", "Zulu", "Alpha");

        // A challenge added later goes to the end
        send(post("/api/ctf-entity"), Map.of("question", "Echo", "roomId", room.getId(), "points", 50,
                "maxAttempts", 3), author).andExpect(status().isCreated());
        JsonNode after = data(call(get("/api/ctf-entity/room/" + room.getId()), author).andReturn());
        assertThat(titles(after, "question")).containsExactly("Mike", "Zulu", "Alpha", "Echo");
    }

    private static List<String> titles(JsonNode list, String field) {
        List<String> titles = new ArrayList<>();
        list.forEach(item -> titles.add(item.get(field).asText()));
        return titles;
    }
}
