package com.infernokun.amaterasu.controllers.entity.lab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.dto.VolumeChangeDTO;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.services.entity.lab.LabService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabConfiguration(@PathVariable String labTrackerId,
                                                                       @PathVariable String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow();
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

        List<VolumeChangeDTO> volumeChanges;
        try {
            volumeChanges = objectMapper.readValue(volumeChangesJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        LOGGER.error(files.toString());

        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LabTracker labTracker = labTrackerOptional.orElseThrow();
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
        LabTracker labTracker = labTrackerOptional.orElseThrow();
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
        LabTracker labTracker = labTrackerOptional.orElseThrow();

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
