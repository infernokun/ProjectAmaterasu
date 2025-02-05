package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.RemoteServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/remote-server")
public class RemoteServerController {
    private final RemoteServerService remoteServerService;
    private final Logger LOGGER = LoggerFactory.getLogger(RemoteServerController.class);

    public RemoteServerController(RemoteServerService remoteServerService) {
        this.remoteServerService = remoteServerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RemoteServer>>> getAllRemoteServers() {
        List<RemoteServer> remoteServers = remoteServerService.getAllServers();
        return ResponseEntity.ok(
                ApiResponse.<List<RemoteServer>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server retrieved successfully.")
                        .data(remoteServers)
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RemoteServer>> getRemoteServerById(@PathVariable String id) {
        Optional<RemoteServer> remoteServerOptional = remoteServerService.getServerById(id);
        return remoteServerOptional.map(remoteServer -> ResponseEntity.ok(
                ApiResponse.<RemoteServer>builder()
                        .code(HttpStatus.OK.value())
                        .data(remoteServer)
                        .build()
        )).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<RemoteServer>builder()
                        .code(HttpStatus.NOT_FOUND.value())
                        .message("Remote server not found.")
                        .data(null)
                        .build()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RemoteServer>> createRemoteServer(@RequestBody RemoteServer remoteServer) {
        RemoteServer createdServer = remoteServerService.addServer(remoteServer);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RemoteServer>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Remote server created successfully.")
                        .data(createdServer)
                        .build()
        );
    }
}