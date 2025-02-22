package com.infernokun.amaterasu.templates;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.ProxmoxResponse;
import com.infernokun.amaterasu.models.ProxmoxVM;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.*;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProxmoxRestTemplate {
    private final RestTemplate restTemplate;
    private final AmaterasuConfig amaterasuConfig;
    private static final String PROXMOX_API_URL = "https://10.0.0.250:8006/api2/json/nodes/inferno/qemu";

    // Constructor now uses the custom RestTemplate
    public ProxmoxRestTemplate(AmaterasuConfig amaterasuConfig) {
        this.restTemplate = createRestTemplate();
        this.amaterasuConfig = amaterasuConfig;
    }

    public List<ProxmoxVM> getVMs() {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<ProxmoxResponse> response = restTemplate.exchange(
                PROXMOX_API_URL, HttpMethod.GET, entity, ProxmoxResponse.class
        );

        if (response.getBody() != null) {
            return Arrays.stream(response.getBody().getData())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public List<ProxmoxVM> getVMTemplates() {
        return getVMs().stream()
                .filter(ProxmoxVM::isTemplate)
                .collect(Collectors.toList());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PVEAPIToken " + amaterasuConfig.getProxmoxAPIToken());
        headers.set("Accept", "application/json");
        return headers;
    }

    public static RestTemplate createRestTemplate() {
        try {
            // Create SSL context that ignores certificate verification
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((X509Certificate[] chain, String authType) -> true)
                    .build();

            // Create SSL Socket Factory
            TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
                    sslContext,
                    HostnameVerificationPolicy.CLIENT,
                    NoopHostnameVerifier.INSTANCE);


            // Create a connection manager with the registry
            PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(tlsSocketStrategy)
                    .build();

            // Optional: Set timeouts
            connectionManager.setDefaultConnectionConfig(
                    ConnectionConfig.custom().setSocketTimeout(Timeout.ofSeconds(30)).build()
            );

            // Create HttpClient with the custom connection manager
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            // Use HttpClient in RestTemplate
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-ignoring RestTemplate", e);
        }
    }
}
