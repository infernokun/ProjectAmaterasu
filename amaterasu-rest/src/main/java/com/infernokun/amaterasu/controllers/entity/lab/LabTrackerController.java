package com.infernokun.amaterasu.controllers.entity.lab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.alt.VolumeChangeRequest;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.services.entity.lab.LabService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import com.infernokun.amaterasu.utils.AESUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-tracker")
public class LabTrackerController extends BaseController {
    private final LabTrackerService labTrackerService;
    private final RemoteServerService remoteServerService;
    private final LabService labService;
    private final CurrentUser currentUser;
    private final AESUtil aesUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabTracker>>> getAllLabTrackers(@RequestParam(required = false) HashMap<String, String> params) {
        if (!currentUser.isAdmin()) {
            // Members only ever see their own team's labs
            Team team = currentUser.user().getTeam();
            return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                    .code(HttpStatus.OK.value())
                    .message("Lab trackers for your team retrieved successfully.")
                    .data(team == null ? List.of() : labTrackerService.findLabTrackerByTeamId(team.getId()))
                    .build());
        }
        if (!params.isEmpty()) {
            if (params.containsKey("teamId")) {
                return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab trackers by team " + params.get("teamId") + " retrieved successfully.")
                        .data(labTrackerService.findLabTrackerByTeamId(params.get("teamId")))
                        .build());
            }
            else if (params.containsKey("labStatus")) {
                return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab trackers by team " + params.get("teamId") + " retrieved successfully.")
                        .data(labTrackerService.findLabTrackerByLabStatus(LabStatus.valueOf(params.get("labStatus"))))
                        .build());
            }
        }
        return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                .code(HttpStatus.OK.value())
                .message("Lab trackers retrieved successfully.")
                .data(labTrackerService.findAllLabTrackers())
                .build());
    }

    public record MoveRequest(String remoteServerId, boolean startFresh) {
    }

    /**
     * Admins move a team's lab to another server. A deployed lab is only moved with startFresh: its
     * copy is destroyed and a fresh one started on the new server.
     */
    @PostMapping("{labTrackerId}/move")
    public ResponseEntity<ApiResponse<LabActionResult>> moveLabTracker(@PathVariable String labTrackerId,
                                                                       @RequestBody MoveRequest request) {
        if (request == null || request.remoteServerId() == null || request.remoteServerId().isBlank()) {
            throw new IllegalArgumentException("Pick a server to move the lab to");
        }
        RemoteServer target = remoteServerService.findServerById(request.remoteServerId());
        LabActionResult result = labService.moveLab(labTrackerId, target, request.startFresh());
        HttpStatus status = result.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ApiResponse.<LabActionResult>builder()
                .code(status.value())
                .message(result.getOutput())
                .data(result)
                .build());
    }

    @GetMapping("{labTrackerId}")
    public ResponseEntity<ApiResponse<LabTracker>> getLabTrackerById(@PathVariable String labTrackerId) {
        Optional<LabTracker> labTracker = labTrackerService.findLabTrackerById(labTrackerId);
        labTracker.ifPresent(currentUser::requireTrackerAccess);
        return labTracker.isPresent()
                ? createSuccessResponse(labTracker.get(), "Lab tracker " + labTrackerId + " retrieved successfully.")
                : createNotFoundResponse(LabTracker.class, labTrackerId);
    }

    /** How to log in to a copy's VMs: the bastion's SSH address and the copy's password. */
    public record LabAccess(String username, String password, String address, String labSubnet, String bastion) {
    }

    /** Only the owning team and admins may read the password; it is decrypted here and never logged. */
    @GetMapping("{labTrackerId}/access")
    public ResponseEntity<ApiResponse<LabAccess>> getLabAccess(@PathVariable String labTrackerId) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerAccess(labTracker);
        List<ProxmoxVM> vms = labTracker.getVms() != null ? labTracker.getVms() : List.of();
        ProxmoxVM bastion = vms.stream().filter(ProxmoxVM::isBastion).findFirst().orElse(null);
        if (bastion == null && labTracker.getAccessPassword() == null) {
            throw new ResourceNotFoundException("This lab has no VM login");
        }
        String username = bastion != null && bastion.getCiuser() != null ? bastion.getCiuser()
                : vms.stream().map(ProxmoxVM::getCiuser).filter(Objects::nonNull).findFirst().orElse(null);
        String password = labTracker.getAccessPassword() != null ? aesUtil.decrypt(labTracker.getAccessPassword()) : null;
        return createSuccessResponse(new LabAccess(username, password,
                bastion != null ? bastion.getAccessAddress() : null,
                bastion != null ? bastion.getLabSubnet() : null,
                bastion != null ? bastion.getName() : null), "Lab access");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LabTracker>> createLabTracker(@RequestBody LabTracker labTracker) {
        LabTracker createdLabTracker = labTrackerService.createLabTracker(labTracker);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<LabTracker>builder()
                .code(HttpStatus.CREATED.value())
                .message("Lab tracker created successfully.")
                .data(createdLabTracker)
                .build());
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<LabTracker>>> createManyLabTrackers(@RequestBody List<LabTracker> labTrackers) {
        List<LabTracker> createdLabTrackers = labTrackerService.createManyLabTrackers(labTrackers);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<LabTracker>>builder()
                .code(HttpStatus.CREATED.value())
                .message("Lab trackers created successfully.")
                .data(createdLabTrackers)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<LabTracker>> deleteLabTracker(@PathVariable String id) {
        LabTracker deletedLabTracker = labTrackerService.deleteLabTracker(id);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<LabTracker>builder()
                .code(HttpStatus.OK.value())
                .message("Lab tracker deleted successfully.")
                .data(deletedLabTracker)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LabTracker>> updateLabTracker(@RequestBody LabTracker labTracker) {
        // The password never reaches the client, so an edit keeps the stored one
        if (labTracker.getId() != null) {
            labTrackerService.findLabTrackerById(labTracker.getId())
                    .ifPresent(existing -> labTracker.setAccessPassword(existing.getAccessPassword()));
        }
        LabTracker updatedLabTracker = labTrackerService.updateLabTracker(labTracker);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<LabTracker>builder()
                .code(HttpStatus.OK.value())
                .message( "Lab tracker updated successfully.")
                .data(updatedLabTracker)
                .build());
    }

    @GetMapping("/settings/{labTrackerId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabConfiguration(@PathVariable String labTrackerId,
                                                                       @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerAccess(labTracker);
        Map<String, Object> response = labService.getLabConfiguration(labTracker, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("LabTracker id %s settings retrieved!", labTrackerId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping(value = "/settings/{labTrackerId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<List<RemoteCommandResponse>>> addVolumeFiles(@PathVariable String labTrackerId,
                                                               @PathVariable String remoteServerId,
                                                               @RequestParam("volumeChanges") String volumeChangesJson,
                                                               @RequestParam("files") List<MultipartFile> files) {

        // Deserialize volumeChanges JSON
        ObjectMapper objectMapper = new ObjectMapper();

        List<VolumeChangeRequest> volumeChanges;
        try {
            volumeChanges = objectMapper.readValue(volumeChangesJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerControl(labTracker);
        List<RemoteCommandResponse> result = labTrackerService.addVolumeFiles(labTracker, remoteServer, volumeChanges, files);

        return ResponseEntity.ok(
                ApiResponse.<List<RemoteCommandResponse>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Upload for id %s success!", labTrackerId))
                        .data(result)
                        .build()
        );
    }


    @GetMapping("/logs/{labTrackerId}/{remoteServerId}/{service}")
    public ResponseEntity<ApiResponse<String>> getLabLogs(@PathVariable String labTrackerId,
                                                                       @PathVariable String remoteServerId,
                                                          @PathVariable String service) {

        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerAccess(labTracker);
        String response = labTrackerService.getLabLogs(labTracker, remoteServer, service);

        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("LabTracker id %s logs retrieved!", labTrackerId))
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/refresh/{labTrackerId}")
    public ResponseEntity<ApiResponse<LabTracker>> refreshLabTracker(@PathVariable String labTrackerId) {
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerAccess(labTracker);

        LabTracker refreshedLabTracker = labTrackerService.refreshLabTracker(labTracker);
        return ResponseEntity.ok(
                ApiResponse.<LabTracker>builder()
                        .code(HttpStatus.OK.value())
                        .message(labTracker.equals(refreshedLabTracker) ?
                                String.format("No updates to the lab tracker: %s", labTrackerId) : String.format("LabTracker id %s updated!", labTrackerId) )
                        .data(refreshedLabTracker)
                        .build()
        );
    }
}
