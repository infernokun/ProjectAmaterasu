package com.infernokun.amaterasu.controllers.entity.lab;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionRequest;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.lab.dto.LabRequest;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.entity.lab.LabService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/labs")
public class LabController extends BaseController {
    private final LabService labService;
    private final LabFileUploadService labFileUploadService;
    private final RemoteServerService remoteServerService;
    private final CurrentUser currentUser;

    /** Authors and admins get drafts too; everyone else only sees published labs. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Lab>>> getAllLabs() {
        return ResponseEntity.ok(
                ApiResponse.<List<Lab>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Labs retrieved successfully.")
                        .data(isAuthor() ? labService.findAllLabs() : labService.findPublishedLabs())
                        .build()
        );
    }

    @GetMapping("{labId}")
    public ResponseEntity<ApiResponse<Lab>> getLabById(@PathVariable String labId) {
        Lab lab = labService.findLabById(labId);
        if (!isAuthor() && !LabService.isPublished(lab)) {
            throw new ResourceNotFoundException("Lab " + labId + " not found");
        }
        return ResponseEntity.ok(
                ApiResponse.<Lab>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab retrieved successfully.")
                        .data(lab)
                        .build()
        );
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<Lab>> publishLab(@PathVariable String id) {
        return createSuccessResponse(labService.publish(id), "Lab published.");
    }

    @PutMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<Lab>> unpublishLab(@PathVariable String id) {
        return createSuccessResponse(labService.unpublish(id), "Lab is a draft again.");
    }

    private boolean isAuthor() {
        return currentUser.isAdmin() || currentUser.hasRole(Role.CREATOR);
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
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        boolean isLabReady = labService.checkDockerComposeValidity(labId, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s is ready!", labId))
                        .data(isLabReady)
                        .build()
        );
    }

    @GetMapping("/settings/{labId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabFile(@PathVariable String labId, @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Lab lab = labService.findLabById(labId);
        Map<String, Object> response = labService.getLabFile(lab, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s settings retrieved!", labId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping()
    public ResponseEntity<ApiResponse<Lab>> createLab(@RequestParam String remoteServerId, @RequestBody LabRequest labRequest) {
        if (labRequest == null) throw new RuntimeException("labDTO is null");
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);

        Lab createdLab = labService.createLab(labRequest, remoteServer);
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

    /** Edits a lab's name, description, version, team copies and, for VM labs, its templates. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Lab>> updateLab(@PathVariable String id, @RequestBody LabRequest changes) {
        Lab updatedLab = labService.editLab(id, changes);
        return ResponseEntity.ok(
                ApiResponse.<Lab>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab updated successfully.")
                        .data(updatedLab)
                        .build()
        );
    }

    @PostMapping("/upload/{labId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<String>> uploadLabFile(@PathVariable String labId, @PathVariable String remoteServerId, @RequestBody String content) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        String response = labService.uploadLabFile(labId, content, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s successfully uploaded!", labId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateDockerComposeYml(@RequestBody String content) {
        boolean isValid = labFileUploadService.validateDockerComposeFile(content);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message(isValid ? "YML successfully validated!" : "YML validation unsuccessful!")
                        .data(isValid)
                        .build()
        );

    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<LabActionResult>> startLab(@RequestBody LabActionRequest labRequest) {
        if (labRequest.getRemoteServerId() == null || labRequest.getRemoteServerId().isBlank()) {
            throw new IllegalArgumentException("remoteServerId is required");
        }
        RemoteServer remoteServer = remoteServerService.findServerById(labRequest.getRemoteServerId());

        LabActionResult result = labService.startLab(labRequest.getLabId(),
                currentUser.id(), labRequest.getLabTrackerId(), remoteServer);

        return ResponseEntity.status(result.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(result.isSuccessful() ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value())
                        .message(result.isSuccessful() ? "Lab started successfully." : "Lab did not start successfully.")
                        .data(result)
                        .build());
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<LabActionResult>> pauseLab(@RequestBody LabActionRequest labRequest) {
        RemoteServer remoteServer = optionalServer(labRequest.getRemoteServerId());

        LabActionResult pausedLab = labService.pauseLab(
                labRequest.getLabId(), currentUser.id(), labRequest.getLabTrackerId(), remoteServer);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message(pausedLab.isSuccessful() ? "Lab paused successfully." : "Lab failed to pause.")
                        .data(pausedLab)
                        .build());
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<LabActionResult>> stopLab(@RequestBody LabActionRequest labRequest) {
        RemoteServer remoteServer = optionalServer(labRequest.getRemoteServerId());

        LabActionResult stoppedLab = labService.stopLab(
                labRequest.getLabId(), currentUser.id(), labRequest.getLabTrackerId(), remoteServer);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message(stoppedLab.isSuccessful() ? "Lab stopped successfully." : "Lab failed to stop.")
                        .data(stoppedLab)
                        .build());
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<LabActionResult>> deleteLab(@RequestBody LabActionRequest labRequest) {
        RemoteServer remoteServer = optionalServer(labRequest.getRemoteServerId());

        LabActionResult deletedLab = labService.deleteLab(
                labRequest.getLabId(), currentUser.id(), labRequest.getLabTrackerId(), remoteServer);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(HttpStatus.OK.value())
                        .message(deletedLab.isSuccessful() ? "Lab deleted successfully." : "Lab failed to delete.")
                        .data(deletedLab)
                        .build());
    }

    @PostMapping("delete-lab-from-team")
    public ResponseEntity<ApiResponse<LabTracker>> deleteLabFromTeam(@RequestBody LabActionRequest labRequest) {
        LabTracker deletedLab = labService.deleteLabFromTeam(labRequest.getLabId(), currentUser.id(),
                        labRequest.getLabTrackerId());

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<LabTracker>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab removed from team.")
                        .data(deletedLab)
                        .build());
    }

    /** Removes a lab from the catalog; refused with 409 while any team has it deployed. */
    @DeleteMapping("/{labId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteLabFromCatalog(@PathVariable String labId) {
        if (!labService.deleteLabItem(labId)) {
            throw new ResourceNotFoundException("Lab " + labId + " not found");
        }
        return createSuccessResponse(true, "Lab deleted.");
    }

    @DeleteMapping("delete-item/{labId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteLabItem(@PathVariable String labId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab item deleted successfully.")
                        .data(this.labService.deleteLabItem(labId))
                        .build());
    }

    @PostMapping("dev")
    public void clear(@RequestBody LabActionRequest labRequest) {
        labService.clear(labRequest.getTeamId());
    }

    private RemoteServer optionalServer(String remoteServerId) {
        return remoteServerId == null || remoteServerId.isBlank() ? null : remoteServerService.findServerById(remoteServerId);
    }
}
