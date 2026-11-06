package com.infernokun.amaterasu.controllers.entity.lab;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.ServerCheck;
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

    /** Tests a server before it is added. A failed test says why; a usable one may carry a warning. */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ServerCheck>> validateRemoteServer(@RequestBody RemoteServerRequest remoteServer) {
        ServerCheck check = remoteServerService.validateRemoteServer(modelMapper.map(remoteServer, RemoteServer.class));
        return createSuccessResponse(check, checkMessage(check));
    }

    private static String checkMessage(ServerCheck check) {
        if (!check.connected()) {
            return check.reason() != null ? check.reason() : "Could not connect with these settings.";
        }
        return check.warning() != null ? check.warning() : "Connected.";
    }

    /** The editable fields of a saved server, without its credentials. */
    @GetMapping("/{id}/settings")
    public ResponseEntity<ApiResponse<RemoteServerService.Settings>> getSettings(@PathVariable String id) {
        return createSuccessResponse(remoteServerService.settings(id), "Server settings retrieved.");
    }

    /** Tests a saved server with edits that are not saved yet; blank credentials use the stored ones. */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<ServerCheck>> validateChanges(@PathVariable String id,
                                                                    @RequestBody RemoteServerRequest changes) {
        ServerCheck check = remoteServerService.validateRemoteServer(remoteServerService.withChanges(id, changes));
        return createSuccessResponse(check, checkMessage(check));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RemoteServer>> updateRemoteServer(@PathVariable String id,
                                                                        @RequestBody RemoteServerRequest changes) {
        List<String> changed = remoteServerService.updateServer(id, changes);
        RemoteServer server = remoteServerService.findServerById(id);
        if (!changed.isEmpty()) {
            auditService.record("SERVER_UPDATED", "SERVER", id, server.getName(), null,
                    "Changed " + String.join(", ", changed));
        }
        return createSuccessResponse(server, changed.isEmpty() ? "Nothing changed." : "Server saved.");
    }

    public record NewLabsRequest(Boolean allowed) {
    }

    /** Admins stop or allow new labs on a server; labs already there keep running, pausing and resuming. */
    @PutMapping("/{id}/new-labs")
    public ResponseEntity<ApiResponse<RemoteServer>> setNewLabs(@PathVariable String id, @RequestBody NewLabsRequest request) {
        if (request == null || request.allowed() == null) {
            throw new IllegalArgumentException("Say whether new labs are allowed");
        }
        RemoteServer server = remoteServerService.setNoNewLabs(id, !request.allowed());
        auditService.record(request.allowed() ? "SERVER_NEW_LABS_ALLOWED" : "SERVER_NEW_LABS_STOPPED", "SERVER", id,
                server.getName(), null, request.allowed() ? "New labs can start here again" : "No new labs start here");
        return createSuccessResponse(server, request.allowed() ? "New labs can start here." : "No new labs start here.");
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