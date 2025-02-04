package com.infernokun.amaterasu.templates;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AmaterasuRestTemplate {

    private final AmaterasuConfig amaterasuConfig;
    private final RestTemplate restTemplate;

    public AmaterasuRestTemplate(AmaterasuConfig amaterasuConfig, RestTemplate restTemplate) {
        this.amaterasuConfig = amaterasuConfig;
        this.restTemplate = restTemplate;
    }

    public String getDockerContainers() {
        String url = String.format("http://%s:2375/containers/json", amaterasuConfig.getDockerHost());

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(url)
                .queryParam("all", true);

        ResponseEntity<String> response = restTemplate.getForEntity(uriBuilder.toUriString(), String.class);

        return response.getBody();
    }
}
