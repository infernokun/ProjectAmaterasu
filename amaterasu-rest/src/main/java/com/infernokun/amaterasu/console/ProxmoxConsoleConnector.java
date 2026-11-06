package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Opens the node's vncwebsocket. Nodes use self-signed certificates, so, like the REST client in
 * {@link ProxmoxService}, this does not check the certificate.
 */
@Component
public class ProxmoxConsoleConnector {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final ProxmoxService proxmoxService;
    private final HttpClient client;

    public ProxmoxConsoleConnector(ProxmoxService proxmoxService) {
        this.proxmoxService = proxmoxService;
        this.client = HttpClient.newBuilder()
                .sslContext(trustingContext())
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public CompletableFuture<WebSocket> connect(RemoteServer server, int vmid, ProxmoxVncTicket ticket,
                                                WebSocket.Listener listener) {
        return client.newWebSocketBuilder()
                .header("Authorization", proxmoxService.authorization(server))
                .subprotocols("binary")
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(proxmoxService.vncWebSocketUri(server, vmid, ticket), listener);
    }

    private static SSLContext trustingContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new TrustingManager()}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not create the console TLS context", e);
        }
    }

    // An extended trust manager, so the JDK does not add its own host name check on top
    private static final class TrustingManager extends X509ExtendedTrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) { }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
