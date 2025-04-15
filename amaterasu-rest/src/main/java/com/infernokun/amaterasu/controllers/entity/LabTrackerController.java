package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.alt.LabReadinessService;
import com.infernokun.amaterasu.services.entity.LabService;
import com.infernokun.amaterasu.services.entity.LabTrackerService;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/lab-tracker")
public class LabTrackerController extends BaseController {
    private final LabTrackerService labTrackerService;
    private final RemoteServerService remoteServerService;
    private final LabService labService;
    private final LabReadinessService labReadinessService;

    public LabTrackerController(LabTrackerService labTrackerService, RemoteServerService remoteServerService, LabService labService, LabReadinessService labReadinessService) {
        this.labTrackerService = labTrackerService;
        this.remoteServerService = remoteServerService;
        this.labService = labService;
        this.labReadinessService = labReadinessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabTracker>>> getAllLabTrackers(@RequestParam(required = false) HashMap<String, String> params) {
        if (!params.isEmpty()) {
            if (params.containsKey("teamId")) {
                return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab trackers by team " + params.get("teamId") + " retrieved successfully.")
                        .data(labTrackerService.findLabTrackerByTeamId(params.get("teamId")))
                        .build());
            }
        }
        return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                .code(HttpStatus.OK.value())
                .message("Lab trackers retrieved successfully.")
                .data(labTrackerService.findAllLabTrackers())
                .build());
    }

    @GetMapping("{labTrackerId}")
    public ResponseEntity<ApiResponse<LabTracker>> getLabTrackerById(@PathVariable String labTrackerId) {
        Optional<LabTracker> labTracker = labTrackerService.findLabTrackerById(labTrackerId);
        return labTracker.isPresent()
                ? createSuccessResponse(labTracker.get(), "Lab tracker " + labTrackerId + " retrieved successfully.")
                : createNotFoundResponse(LabTracker.class, labTrackerId);
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
        LabTracker updatedLabTracker = labTrackerService.updateLabTracker(labTracker);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<LabTracker>builder()
                .code(HttpStatus.OK.value())
                .message( "Lab tracker updated successfully.")
                .data(updatedLabTracker)
                .build());
    }

    @GetMapping("/settings/{labTrackerId}/{remoteServerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabFile(@PathVariable String labTrackerId,
                                                                       @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow();
        Map<String, Object> response = labService.getLabFile(labTracker, remoteServer);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("LabTracker id %s settings retrieved!", labTrackerId))
                        .data(response)
                        .build()
        );
    }
}
