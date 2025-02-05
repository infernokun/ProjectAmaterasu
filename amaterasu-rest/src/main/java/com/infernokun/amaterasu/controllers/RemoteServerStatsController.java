package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.services.RemoteServerStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/remote-server-stats")
public class RemoteServerStatsController {
    private final RemoteServerStatsService remoteServerStatsService;
    private final Logger LOGGER = LoggerFactory.getLogger(RemoteServerStatsController.class);

    public RemoteServerStatsController(RemoteServerStatsService remoteServerStatsService) {
        this.remoteServerStatsService = remoteServerStatsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RemoteServerStats>>> getAllStats() {
        List<RemoteServerStats> stats = remoteServerStatsService.findAllStats();
        return ResponseEntity.ok(
                ApiResponse.<List<RemoteServerStats>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server stats retrieved successfully.")
                        .data(stats)
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RemoteServerStats>> getStatsById(@PathVariable String id) {
        Optional<RemoteServerStats> statsOptional = remoteServerStatsService.findStatsById(id);
        return statsOptional.map(remoteServerStats -> ResponseEntity.ok(
                ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.OK.value())
                        .data(remoteServerStats)
                        .build()
        )).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.NOT_FOUND.value())
                        .message("Remote server stats not found.")
                        .data(null)
                        .build()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RemoteServerStats>> createStats(@RequestBody RemoteServerStats stats) {
        RemoteServerStats createdStats = remoteServerStatsService.createStats(stats);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Remote server stats created successfully.")
                        .data(createdStats)
                        .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RemoteServerStats>> updateStats(
            @PathVariable String id, @RequestBody RemoteServerStats stats) {
        // Ensure the given ID matches the stats object if necessary
        stats.setId(id);
        RemoteServerStats updatedStats = remoteServerStatsService.updateStats(stats);
        return ResponseEntity.ok(
                ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server stats updated successfully.")
                        .data(updatedStats)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStats(@PathVariable String id) {
        boolean isDeleted = remoteServerStatsService.deleteStats(id);
        if (isDeleted) {
            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .code(HttpStatus.OK.value())
                            .message("Remote server stats deleted successfully.")
                            .data(null)
                            .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Void>builder()
                            .code(HttpStatus.NOT_FOUND.value())
                            .message("Remote server stats not found or conflict occurred.")
                            .data(null)
                            .build());
        }
    }
}