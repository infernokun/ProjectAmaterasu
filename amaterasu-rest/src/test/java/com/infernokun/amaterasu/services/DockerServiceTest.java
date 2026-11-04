package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DockerServiceTest {

    private DockerService dockerService;
    private LabTracker tracker;

    @BeforeEach
    void setUp() {
        AmaterasuConfig config = new AmaterasuConfig();
        config.setUploadDir("/var/tmp/amaterasu");
        dockerService = new DockerService(mock(RemoteCommandService.class), config);

        Lab lab = new Lab();
        lab.setId("lab1");
        lab.setDockerFile("web.yml");
        tracker = new LabTracker();
        tracker.setId("t1");
        tracker.setLabStarted(lab);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> service(Map<String, Object> compose, String name) {
        return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rewriteKeepsDeploymentsApart() {
        String yaml = """
                services:
                  web:
                    image: nginx
                    container_name: web
                    ports: ["80:80"]
                    volumes:
                      - ./html:/usr/share/nginx/html:ro
                      - ./conf/site.conf:/etc/nginx/conf.d/site.conf
                      - data:/data
                      - logs:/logs
                      - /cache
                      - type: bind
                        source: ../../etc
                        target: /host
                volumes:
                  data:
                    name: shared-data
                  logs:
                  ext:
                    external: true
                    name: keep-me
                networks:
                  lan:
                    name: fixed-lan
                """;

        DockerService.ComposeRewrite rewrite = dockerService.rewriteCompose(yaml, tracker);
        Map<String, Object> compose = new Yaml().load(rewrite.yaml());
        Map<String, Object> web = service(compose, "web");

        assertThat(web).doesNotContainKeys("ports", "container_name");
        List<Object> volumes = (List<Object>) web.get("volumes");
        String base = "/var/tmp/amaterasu/tracker-volume/t1/web";
        assertThat(volumes).containsSubsequence(
                base + "/html:/usr/share/nginx/html:ro",
                base + "/conf/site.conf:/etc/nginx/conf.d/site.conf",
                "data:/data",
                "logs:/logs",
                "/cache");
        assertThat(((Map<String, Object>) volumes.get(5)).get("source")).isEqualTo(base + "/etc");
        assertThat(rewrite.bindSources()).containsExactly(base + "/html", base + "/conf/site.conf", base + "/etc");

        Map<String, Object> topVolumes = (Map<String, Object>) compose.get("volumes");
        assertThat((Map<String, Object>) topVolumes.get("data")).doesNotContainKey("name");
        assertThat(((Map<String, Object>) topVolumes.get("ext")).get("name")).isEqualTo("keep-me");
        assertThat((Map<String, Object>) ((Map<String, Object>) compose.get("networks")).get("lan")).doesNotContainKey("name");
    }

    @Test
    void pathsAreBuiltFromTracker() {
        assertThat(dockerService.labFilePath(tracker.getLabStarted())).isEqualTo("/var/tmp/amaterasu/lab1/web.yml");
        assertThat(dockerService.trackerFilePath(tracker)).isEqualTo("/var/tmp/amaterasu/tracker-compose/t1_web.yml");
        assertThat(dockerService.trackerVolumeDir(tracker)).isEqualTo("/var/tmp/amaterasu/tracker-volume/t1");
    }

    @Test
    void shellQuoteSurvivesQuotes() {
        assertThat(RemoteCommandService.shellQuote("Bob's lab")).isEqualTo("'Bob'\\''s lab'");
    }
}
