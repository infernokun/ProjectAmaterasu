package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabStartRequest;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.services.LabService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/labs")
public class LabController {

    @Value("${amaterasu.uploadDir}")  // Path from application.yml
    private String uploadDir;

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Lab>>> getAllLabs() {
        List<Lab> labs = labService.findAllLabs();
        return ResponseEntity.ok(ApiResponse.<List<Lab>>builder()
                .code(HttpStatus.OK.value())
                .message("Labs retrieved successfully.")
                .data(labs)
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Lab>> createLab(@RequestBody Lab lab) {
        Lab createdLab = labService.createLab(lab);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Lab>builder()
                .code(HttpStatus.CREATED.value())
                .message("Lab created successfully.")
                .data(createdLab)
                .build());
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<Lab>>> createManyLabs(@RequestBody List<Lab> labs) {
        List<Lab> createdLabs = labService.createManyLabs(labs);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<Lab>>builder()
                .code(HttpStatus.CREATED.value())
                .message("Labs created successfully.")
                .data(createdLabs)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLab(@PathVariable String id) {
        boolean isDeleted = labService.deleteLab(id);

        if (isDeleted) {
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(HttpStatus.OK.value())
                    .message("Lab deleted successfully.")
                    .data(null) // No additional data for delete operation
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("Lab not found or a conflict occurred.")
                    .data(null)
                    .build());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Lab>> updateLab(@RequestBody Lab lab) {
        Lab updatedLab = labService.updateLab(lab);
        if (updatedLab != null) {
            return ResponseEntity.ok(ApiResponse.<Lab>builder()
                    .code(HttpStatus.OK.value())
                    .message("Lab updated successfully.")
                    .data(updatedLab)
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Lab>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("Lab not found.")
                    .data(null)
                    .build());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<LabTracker>> startLab(@RequestBody LabStartRequest labStartRequest) {
        try {
            Optional<LabTracker> startedLab = labService.startLab(labStartRequest.getLabId(), labStartRequest.getUserId());

            return ResponseEntity.status(startedLab.isPresent() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<LabTracker>builder()
                            .code(startedLab.isPresent() ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value())
                            .message(startedLab.isPresent() ? "Lab started successfully." : "Failed to start the lab.")
                            .data(startedLab.orElse(null))
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<LabTracker>builder()
                    .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message("An error occurred: " + e.getMessage())
                    .data(null)
                    .build());
        }
    }
}
