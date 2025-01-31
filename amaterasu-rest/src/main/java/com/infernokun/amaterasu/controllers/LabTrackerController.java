package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.LabTracker; // Assuming you have a LabTracker entity
import com.infernokun.amaterasu.services.LabTrackerService; // Assuming you have a LabTrackerService
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lab-tracker")
public class LabTrackerController {

    private final LabTrackerService labTrackerService;

    public LabTrackerController(LabTrackerService labTrackerService) {
        this.labTrackerService = labTrackerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabTracker>>> getAllLabTrackers() {
        List<LabTracker> labTrackers = labTrackerService.findAllLabTrackers();
        return ResponseEntity.ok(ApiResponse.<List<LabTracker>>builder()
                .code(HttpStatus.OK.value())
                .message("Lab trackers retrieved successfully.")
                .data(labTrackers)
                .build());
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
    public ResponseEntity<ApiResponse<Boolean>> deleteLabTracker(@PathVariable String id) {
        boolean isDeleted = labTrackerService.deleteLabTracker(id);

        return ResponseEntity.status(isDeleted ? HttpStatus.OK : HttpStatus.NOT_FOUND).body(ApiResponse.<Boolean>builder()
                .code(isDeleted ? HttpStatus.OK.value() :HttpStatus.NOT_FOUND.value() )
                .message(isDeleted ? "Lab tracker deleted successfully." : "Lab tracker not found or a conflict occurred.")
                .data(isDeleted) // No additional data for delete operation
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LabTracker>> updateLabTracker(@RequestBody LabTracker labTracker) {
        LabTracker updatedLabTracker = labTrackerService.updateLabTracker(labTracker);

        return ResponseEntity.status(updatedLabTracker != null ? HttpStatus.OK : HttpStatus.NOT_FOUND).body(ApiResponse.<LabTracker>builder()
                .code(updatedLabTracker != null ? HttpStatus.OK.value() :HttpStatus.NOT_FOUND.value() )
                .message(updatedLabTracker != null ? "Lab tracker updated successfully." : "Lab tracker not found.")
                .data(updatedLabTracker) // No additional data for delete operation
                .build());
    }
}
