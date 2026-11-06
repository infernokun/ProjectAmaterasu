package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The console ticket request sent to a Proxmox node, and the websocket it points at. */
class ProxmoxServiceVncTest {
    private MockRestServiceServer node;
    private ProxmoxService proxmox;
    private RemoteServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        node = MockRestServiceServer.bindTo(restTemplate).build();
        AESUtil aes = mock(AESUtil.class);
        when(aes.decrypt("encrypted")).thenReturn("root@pam!amaterasu=secret");
        proxmox = new ProxmoxService(aes, restTemplate);
        server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.250").nodeName("inferno")
                .serverType(ServerType.PROXMOX).apiToken("encrypted").build();
        server.setId("server-1");
    }

    @Test
    void asksTheNodeForAWebsocketTicketWithTheApiToken() {
        node.expect(requestTo("https://10.0.0.250:8006/api2/json/nodes/inferno/qemu/101/vncproxy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "PVEAPIToken root@pam!amaterasu=secret"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"websocket\":1}"))
                .andRespond(withSuccess("{\"data\":{\"port\":\"5900\",\"ticket\":\"PVEVNC:abc+/=\",\"user\":\"root@pam!amaterasu\","
                        + "\"cert\":\"-----BEGIN CERTIFICATE-----\",\"upid\":\"UPID:inferno:1:vncproxy:101:root@pam:\"}}",
                        MediaType.APPLICATION_JSON));

        ProxmoxVncTicket ticket = proxmox.createVncProxy(server, 101);

        node.verify();
        assertThat(ticket.port()).isEqualTo("5900");
        assertThat(ticket.ticket()).isEqualTo("PVEVNC:abc+/=");
        assertThat(proxmox.vncWebSocketUri(server, 101, ticket).toString()).isEqualTo(
                "wss://10.0.0.250:8006/api2/json/nodes/inferno/qemu/101/vncwebsocket?port=5900&vncticket=PVEVNC%3Aabc%2B%2F%3D");
        assertThat(proxmox.authorization(server)).isEqualTo("PVEAPIToken root@pam!amaterasu=secret");
    }

    @Test
    void aNodeErrorReachesTheCaller() {
        node.expect(requestTo("https://10.0.0.250:8006/api2/json/nodes/inferno/qemu/101/vncproxy"))
                .andRespond(withServerError().body("{\"message\":\"VM 101 not running\"}"));
        assertThatThrownBy(() -> proxmox.createVncProxy(server, 101)).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void anAnswerWithoutATicketIsRejected() {
        node.expect(requestTo("https://10.0.0.250:8006/api2/json/nodes/inferno/qemu/101/vncproxy"))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> proxmox.createVncProxy(server, 101)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyProxmoxServersHaveConsoles() {
        server.setServerType(ServerType.DOCKER_HOST);
        assertThatThrownBy(() -> proxmox.createVncProxy(server, 101)).isInstanceOf(IllegalArgumentException.class);
    }
}
