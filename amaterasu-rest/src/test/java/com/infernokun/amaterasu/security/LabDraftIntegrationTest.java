package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.LabType;
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

/** A lab built in the New Lab flow stays a draft, hidden from players, until it is playable and published. */
@SpringBootTest
@AutoConfigureMockMvc
class LabDraftIntegrationTest extends IntegrationSupport {

    @Test
    void draftsStayHiddenUntilTheyArePlayableAndPublished() throws Exception {
        String creator = token(user(Role.CREATOR, null));
        String member = token(user(Role.MEMBER, null));
        JsonNode draft = data(send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "## Situation\nSoon.",
                "published", false), creator).andExpect(status().isCreated()).andReturn());
        String id = draft.get("id").asText();
        assertThat(draft.get("published").asBoolean()).isFalse();

        assertThat(data(call(get("/api/room/cards"), member).andExpect(status().isOk()).andReturn()).toString()).doesNotContain(id);
        assertThat(data(call(get("/api/room"), member).andExpect(status().isOk()).andReturn()).toString()).doesNotContain(id);
        call(get("/api/room/" + id + "/overview"), member).andExpect(status().isNotFound());
        send(post("/api/room/" + id + "/join"), Map.of(), member).andExpect(status().isNotFound());
        call(get("/api/room/" + id + "/overview"), creator).andExpect(status().isOk());

        // Not playable yet: no questions, then a question with no flag, then a draft environment
        send(put("/api/room/" + id), Map.of("published", true), creator).andExpect(status().isBadRequest());
        CTFEntity question = challenge(roomRepository.findById(id).orElseThrow(), "First", null, 0);
        send(put("/api/room/" + id), Map.of("published", true), creator).andExpect(status().isBadRequest());
        Flag flag = new Flag();
        flag.setFlag("one");
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        ctfEntityRepository.save(question);
        Lab environment = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(1)
                .ready(true).published(false).build());
        send(put("/api/room/" + id), Map.of("environmentMode", "SHARED", "environmentId", environment.getId()), creator)
                .andExpect(status().isOk());
        String refused = send(put("/api/room/" + id), Map.of("published", true), creator).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("Publish the environment first");
        send(put("/api/room/" + id), Map.of("environmentMode", "NONE"), creator).andExpect(status().isOk());

        assertThat(data(send(put("/api/room/" + id), Map.of("published", true), creator).andExpect(status().isOk()).andReturn())
                .get("published").asBoolean()).isTrue();
        assertThat(data(call(get("/api/room/cards"), member).andExpect(status().isOk()).andReturn()).toString()).contains(id);
        send(post("/api/room/" + id + "/join"), Map.of(), member).andExpect(status().isOk());

        send(put("/api/room/" + id), Map.of("published", false), creator).andExpect(status().isOk());
        call(get("/api/room/" + id + "/overview"), member).andExpect(status().isNotFound());
    }

    @Test
    void labsMadeThroughTheApiArePublishedUnlessToldOtherwise() throws Exception {
        JsonNode made = data(send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "Go"),
                token(user(Role.CREATOR, null))).andExpect(status().isCreated()).andReturn());
        assertThat(made.get("published").asBoolean()).isTrue();
    }
}
