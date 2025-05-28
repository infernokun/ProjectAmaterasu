package com.infernokun.amaterasu.controllers.entity.lab;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.lab.RemoteServerStats;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/remote-server-stats")
public class RemoteServerStatsController extends BaseController {
    private final RemoteServerStatsService remoteServerStatsService;

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
        RemoteServerStats remoteServerStats = remoteServerStatsService.findStatsById(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Remote server stats found!")
                        .data(remoteServerStats)
                        .build()
        );
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
        //stats.setId(id);
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
    public ResponseEntity<ApiResponse<RemoteServerStats>> deleteStats(@PathVariable String id) {
        RemoteServerStats deletedRemoteServerStats = remoteServerStatsService.deleteStats(id);
        return ResponseEntity.ok(
                ApiResponse.<RemoteServerStats>builder()
                        .code(HttpStatus.OK.value())
                        .message("Remote server stats deleted successfully.")
                        .data(deletedRemoteServerStats)
                        .build());
    }
}