package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.LabRequest;
import com.infernokun.amaterasu.models.dto.LabDTO;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.entity.LabService;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/labs")
public class LabController extends BaseController {
    private final LabService labService;
    private final RemoteServerService remoteServerService;

    public LabController(LabService labService, RemoteServerService remoteServerService) {
        this.labService = labService;
        this.remoteServerService = remoteServerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Lab>>> getAllLabs() {
        return ResponseEntity.ok(
                ApiResponse.<List<Lab>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Labs retrieved successfully.")
                        .data(labService.findAllLabs())
                        .build()
        );
    }

    @GetMapping("{labId}")
    public ResponseEntity<ApiResponse<Lab>> getLabById(@PathVariable String labId) {
        return ResponseEntity.ok(
                ApiResponse.<Lab>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab retrieved successfully.")
                        .data(labService.findLabById(labId))
                        .build()
        );
    }

    /*@GetMapping("/settings/{teamId}/{labId}")
    public ResponseEntity<ApiResponse<Lab>> getLabById(@PathVariable String labId) {
        return ResponseEntity.ok(
                ApiResponse.<Lab>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab retrieved successfully.")
                        .data(labService.findLabById(labId))
                        .build()
        );
    }*/

    @GetMapping("/check/{labId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<Boolean>> checkLabReadiness(@PathVariable String labId, @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.getServerById(remoteServerId);
        boolean isLabReady = labService.checkDockerComposeValidity(labId, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s is ready!", labId))
                        .data(isLabReady)
                        .build()
        );
    }

    @GetMapping("/settings/{labId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabFile(@PathVariable String labId, @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.getServerById(remoteServerId);
        Map<String, Object> response = labService.getLabFile(labId, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s successfully uploaded!", labId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping()
    public ResponseEntity<ApiResponse<Lab>> createLab(@RequestParam String remoteServerId, @RequestBody LabDTO labDTO) {
        if (labDTO == null) throw new RuntimeException("labDTO is null");
        RemoteServer remoteServer = remoteServerService.getServerById(remoteServerId);

        Lab createdLab = labService.createLab(labDTO, remoteServer);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<Lab>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Lab created successfully.")
                        .data(createdLab)
                        .build()
        );
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<Lab>>> createManyLabs(@RequestBody List<Lab> labs) {
        List<Lab> createdLabs = labService.createManyLabs(labs);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<Lab>>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Labs created successfully.")
                        .data(createdLabs)
                        .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Lab>> updateLab(@RequestBody Lab lab) {
        Lab updatedLab = labService.updateLab(lab);
        if (updatedLab != null) {
            return ResponseEntity.ok(
                    ApiResponse.<Lab>builder()
                            .code(HttpStatus.OK.value())
                            .message("Lab updated successfully.")
                            .data(updatedLab)
                            .build()
            );
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Lab>builder()
                            .code(HttpStatus.NOT_FOUND.value())
                            .message("Lab not found.")
                            .data(null)
                            .build()
            );
        }
    }

    @PostMapping("/upload/{labId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<String>> uploadLabFile(@PathVariable String labId, @PathVariable String remoteServerId, @RequestBody String content) {
        RemoteServer remoteServer = remoteServerService.getServerById(remoteServerId);
        String response = labService.uploadLabFile(labId, content, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s successfully uploaded!", labId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<LabActionResult>> startLab(@RequestBody LabRequest labRequest) {
        RemoteServer remoteServer = remoteServerService.getServerById(labRequest.getRemoteServerId());
        LabActionResult startedLabOptional = labService.startLab(labRequest.getLabId(),
                labRequest.getUserId(), labRequest.getLabTrackerId(), remoteServer);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab started successfully.")
                        .data(startedLabOptional)
                        .build());
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<LabActionResult>> stopLab(@RequestBody LabRequest labRequest) {
        RemoteServer remoteServer = remoteServerService.getServerById(labRequest.getRemoteServerId());

        LabActionResult stoppedLab = labService.stopLab(
                labRequest.getLabId(), labRequest.getUserId(), labRequest.getLabTrackerId(), remoteServer);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab stopped successfully.")
                        .data(stoppedLab)
                        .build());
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<LabActionResult>> deleteLab(@RequestBody LabRequest labRequest) {
        RemoteServer remoteServer = remoteServerService.getServerById(labRequest.getRemoteServerId());

        LabActionResult deletedLab = labService.deleteLab(
                labRequest.getLabId(), labRequest.getUserId(), labRequest.getLabTrackerId(), remoteServer);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab deleted successfully.")
                        .data(deletedLab)
                        .build());
    }

    @PostMapping("delete-lab-from-team")
    public ResponseEntity<ApiResponse<LabTracker>> deleteLabFromTeam(@RequestBody LabRequest labRequest) {
        LOGGER.info("Deleting lab with details {}, {}, and {}",
                labRequest.getLabId(), labRequest.getUserId(), labRequest.getLabTrackerId());

        Optional<LabTracker> deletedLab =
                labService.deleteLabFromTeam(labRequest.getLabId(),
                        labRequest.getUserId(),
                        labRequest.getLabTrackerId());

        return ResponseEntity.status(deletedLab.isPresent() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<LabTracker>builder()
                        .code(deletedLab.isPresent() ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value())
                        .message(deletedLab.isPresent() ? "Lab deleted successfully." : "Failed to delete the lab.")
                        .data(deletedLab.orElse(null))
                        .build());
    }

    @PostMapping("dev")
    public void clear(@RequestBody LabRequest labRequest) {
        labService.clear(labRequest.getTeamId());
    }
}
