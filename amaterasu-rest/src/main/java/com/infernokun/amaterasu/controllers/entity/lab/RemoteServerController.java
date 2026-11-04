package com.infernokun.amaterasu.controllers.entity.lab;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.lab.dto.RemoteServerRequest;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.cron.RemoteStatsService;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/remote-server")
public class RemoteServerController extends BaseController {
    private final RemoteServerService remoteServerService;
    private final ModelMapper modelMapper;
    private final RemoteStatsService remoteStatsService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RemoteServer>>> getAllRemoteServers(@RequestParam(required = false) HashMap<String, String> params) {
        if (!params.isEmpty()) {
            if (params.containsKey("serverType")) {
                return ResponseEntity.ok(ApiResponse.<List<RemoteServer>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Server by type " + params.get("serverType") + " retrieved successfully.")
                        .data(remoteServerService.findByServerType(ServerType.valueOf(params.get("serverType"))))
                        .build());
            }
        }
        return ResponseEntity.ok(
                ApiResponse.<List<RemoteServer>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server retrieved successfully.")
                        .data(remoteServerService.findAllServers())
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RemoteServer>> getRemoteServerById(@PathVariable String id) {
        RemoteServer remoteServer = remoteServerService.findServerById(id);
        return ResponseEntity.ok(
                ApiResponse.<RemoteServer>builder()
                        .code(HttpStatus.OK.value())
                        .data(remoteServer)
                        .build()
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RemoteServer>> createRemoteServer(@RequestBody RemoteServerRequest remoteServer) {
        RemoteServer server = modelMapper.map(remoteServer, RemoteServer.class);
        remoteServerService.requireValidNewServer(server);
        server.setName(server.getName().trim());
        RemoteServer createdServer = remoteServerService.addServer(server);
        auditService.record("SERVER_ADDED", "SERVER", createdServer.getId(), createdServer.getName(), null,
                createdServer.getServerType() + " at " + createdServer.getIpAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RemoteServer>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Remote server created successfully.")
                        .data(createdServer)
                        .build()
        );
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateRemoteServer(@RequestBody RemoteServerRequest remoteServer) {
        boolean isRemoteServerValid = remoteServerService.validateRemoteServer(modelMapper.map(remoteServer, RemoteServer.class));
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<Boolean>builder()
                    .code(HttpStatus.OK.value())
                    .message("Remote server status is: " + isRemoteServerValid)
                    .data(isRemoteServerValid)
                    .build()
        );
    }

    /** Checks a saved server now and refreshes its stats. */
    @PostMapping("/{id}/check")
    public ResponseEntity<ApiResponse<RemoteServer>> checkRemoteServer(@PathVariable String id) {
        RemoteServer server = remoteServerService.findServerById(id);
        remoteStatsService.refresh(server);
        RemoteServer checked = remoteServerService.findServerById(id);
        boolean online = checked.getRemoteServerStats() != null
                && checked.getRemoteServerStats().getStatus() == LabStatus.ACTIVE;
        return ResponseEntity.ok(ApiResponse.<RemoteServer>builder()
                .code(HttpStatus.OK.value())
                .message(checked.getName() + (online ? " answered." : " did not answer."))
                .data(checked)
                .build());
    }

    @DeleteMapping("{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteRemoteServer(@PathVariable String id) {
        RemoteServer deleted = remoteServerService.deleteServer(id);
        auditService.record("SERVER_DELETED", "SERVER", id, deleted.getName(), null, null);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server " + deleted.getName() + " deleted.")
                        .data(true)
                        .build()
        );
    }
}