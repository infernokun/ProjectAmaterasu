package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/remote-server")
public class RemoteServerController extends BaseController {
    private final RemoteServerService remoteServerService;

    public RemoteServerController(RemoteServerService remoteServerService) {
        this.remoteServerService = remoteServerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RemoteServer>>> getAllRemoteServers(@RequestParam(required = false) HashMap<String, String> params) {
        if (!params.isEmpty()) {
            if (params.containsKey("teamId")) {
                return ResponseEntity.ok(ApiResponse.<List<RemoteServer>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab trackers by team " + params.get("teamId") + " retrieved successfully.")
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

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateRemoteServer(@RequestBody RemoteServer remoteServer) {
        boolean isRemoteServerValid = remoteServerService.validateRemoteServer(remoteServer);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<Boolean>builder()
                    .code(HttpStatus.OK.value())
                    .message("Remote server status is: " + isRemoteServerValid)
                    .data(isRemoteServerValid)
                    .build()
        );
    }
    @DeleteMapping("{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteRemoteServer(@PathVariable String id) {
        boolean remoteServerDeleted = remoteServerService.deleteServer(id);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server " + id + " deleted!")
                        .data(remoteServerDeleted)
                        .build()
        );
    }
}