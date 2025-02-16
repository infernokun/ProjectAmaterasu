package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.controllers.base.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.LabRequest;
import com.infernokun.amaterasu.models.dto.LabDTO;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.services.LabService;
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

    public LabController(LabService labService) {
        this.labService = labService;
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

    @GetMapping("/check/{labId}")
    public ResponseEntity<ApiResponse<Boolean>> checkLabReadiness(@PathVariable String labId) {
        boolean isLabReady = labService.checkDockerComposeValidity(labId);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s is ready!", labId))
                        .data(isLabReady)
                        .build()
        );
    }

    @GetMapping("/settings/{labId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabFile(@PathVariable String labId) {
        Map<String, Object> response = labService.getLabFile(labId);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .code(HttpStatus.OK.value())
                        .message(String.format("Lab id %s successfully uploaded!", labId))
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Lab>> createLab(@RequestBody LabDTO labDTO) {
        if (labDTO == null) throw new RuntimeException("labDTO is null");

        Lab createdLab = labService.createLab(labDTO);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLab(@PathVariable String id) {
        boolean isDeleted = labService.deleteLab(id);
        if (isDeleted) {
            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .code(HttpStatus.OK.value())
                            .message("Lab deleted successfully.")
                            .data(null)
                            .build()
            );
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Void>builder()
                            .code(HttpStatus.NOT_FOUND.value())
                            .message("Lab not found or a conflict occurred.")
                            .data(null)
                            .build()
            );
        }
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

    @PostMapping("/upload/{labId}")
    public ResponseEntity<ApiResponse<String>> uploadLabFile(@PathVariable String labId, @RequestBody String content) {
        String response = labService.uploadLabFile(labId, content);

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
        Optional<LabActionResult> startedLabOptional =
                labService.startLab(labRequest.getLabId(), labRequest.getUserId(), labRequest.getLabTrackerId());

        return ResponseEntity.status(startedLabOptional.isPresent() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(startedLabOptional.isPresent() ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value())
                        .message(startedLabOptional.isPresent() ? "Lab started successfully." : "Failed to start the lab.")
                        .data(startedLabOptional.orElse(null))
                        .build());
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<LabActionResult>> stopLab(@RequestBody LabRequest labRequest) {
        Optional<LabActionResult> stoppedLabOptional =
                labService.stopLab(labRequest.getLabId(), labRequest.getUserId(), labRequest.getLabTrackerId());
        return ResponseEntity.status(stoppedLabOptional.isPresent() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<LabActionResult>builder()
                        .code(stoppedLabOptional.isPresent() ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value())
                        .message(stoppedLabOptional.isPresent() ? "Lab stopped successfully." : "Failed to stop the lab.")
                        .data(stoppedLabOptional.orElse(null))
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
