package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Files players download from a lab, the questions that name them, and unlimited attempts. */
@SpringBootTest
@AutoConfigureMockMvc
class LabFilesIntegrationTest extends IntegrationSupport {
    private static final byte[] LOG = "10.0.0.1 - - [14/Oct/2026:19:12:57] \"POST /rides\" 500\n".getBytes(StandardCharsets.UTF_8);

    private Room room;
    private String creator;

    @BeforeEach
    void setUp() throws Exception {
        room = roomRepository.save(Room.builder().name(unique("lab")).scenario("Read the logs.").build());
        creator = token(user(Role.CREATOR, null));
    }

    private ResultActions upload(String token, String name, byte[] bytes) throws Exception {
        var request = multipart("/api/room/" + room.getId() + "/files")
                .file(new MockMultipartFile("file", "access.log", "text/plain", bytes));
        if (name != null) {
            request.param("name", name);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    @Test
    void authorsUploadAndPlayersDownload() throws Exception {
        String member = token(user(Role.MEMBER, null));
        upload(member, null, LOG).andExpect(status().isForbidden());
        upload(null, null, LOG).andExpect(status().isUnauthorized());

        JsonNode saved = data(upload(creator, "incident/access.log", LOG).andExpect(status().isCreated()).andReturn());
        assertThat(saved.get("name").asText()).isEqualTo("incident/access.log");
        assertThat(saved.get("size").asLong()).isEqualTo(LOG.length);
        String id = saved.get("id").asText();

        JsonNode list = data(call(get("/api/room/" + room.getId() + "/files"), member).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).has("data")).isFalse();

        byte[] body = call(get("/api/room/" + room.getId() + "/files/" + id), member).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("access.log")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(LOG);

        // The same name replaces the file
        byte[] longer = (new String(LOG, StandardCharsets.UTF_8) + "more\n").getBytes(StandardCharsets.UTF_8);
        JsonNode replaced = data(upload(creator, "incident/access.log", longer).andExpect(status().isCreated()).andReturn());
        assertThat(replaced.get("id").asText()).isEqualTo(id);
        assertThat(replaced.get("size").asLong()).isEqualTo(longer.length);

        call(delete("/api/room/" + room.getId() + "/files/" + id), member).andExpect(status().isForbidden());
        call(delete("/api/room/" + room.getId() + "/files/" + id), creator).andExpect(status().isOk());
        call(get("/api/room/" + room.getId() + "/files/" + id), member).andExpect(status().isNotFound());
    }

    @Test
    void namesCannotClimbOutOfTheLab() throws Exception {
        upload(creator, "../etc/passwd", LOG).andExpect(status().isBadRequest());
        upload(creator, "logs/./x", LOG).andExpect(status().isBadRequest());
        JsonNode saved = data(upload(creator, "/logs//app.log", LOG).andExpect(status().isCreated()).andReturn());
        assertThat(saved.get("name").asText()).isEqualTo("logs/app.log");
    }

    @Test
    void questionsLinkTheFilesTheyNameAndAllowUnlimitedAttempts() throws Exception {
        String fileId = data(upload(creator, "incident/access.log", LOG).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        CTFEntity question = challenge(room, "Size of the evidence", null, 0);
        question.setMaxAttempts(null);
        question.setAttachments(new ArrayList<>(List.of("incident/access.log", "not-uploaded.txt")));
        Flag flag = new Flag();
        flag.setFlag("20464");
        flag.setCaseSensitive(false);
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        ctfEntityRepository.save(question);

        User player = user(Role.MEMBER, null);
        join(player, room);
        String token = token(player);
        JsonNode overview = data(call(get("/api/room/" + room.getId() + "/overview"), token).andExpect(status().isOk()).andReturn());
        assertThat(overview.get("files")).hasSize(1);
        JsonNode attachments = overview.get("challenges").get(0).get("attachments");
        assertThat(attachments.get(0).get("fileId").asText()).isEqualTo(fileId);
        assertThat(attachments.get(1).get("fileId").isNull()).isTrue();
        assertThat(overview.get("challenges").get(0).get("maxAttempts").isNull()).isTrue();

        for (int i = 0; i < 12; i++) {
            send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", question.getId(), "flag", "wrong " + i), token)
                    .andExpect(status().isOk());
        }
        JsonNode right = data(send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", question.getId(),
                "flag", "20464"), token).andExpect(status().isOk()).andReturn());
        assertThat(right.get("correct").asBoolean()).isTrue();
    }
}
