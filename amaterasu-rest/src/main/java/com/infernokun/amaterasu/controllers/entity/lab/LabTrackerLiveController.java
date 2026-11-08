package com.infernokun.amaterasu.controllers.entity.lab;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/** Every team's labs for staff, and restarting one service of a team's lab. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-tracker")
public class LabTrackerLiveController {
    private final LabTrackerService labTrackerService;
    private final DockerService dockerService;
    private final LabActionGuard labActionGuard;
    private final CurrentUser currentUser;

    /** All labs still on a team. SecurityConfig limits this to facilitators and admins. */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<List<LabTracker>>> overview() {
        List<LabTracker> trackers = labTrackerService.findAllLabTrackers().stream()
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED)
                .toList();
        return buildSuccessResponse("Lab trackers for every team", trackers, HttpStatus.OK);
    }

    @PostMapping("/{labTrackerId}/restart/{service}")
    public ResponseEntity<ApiResponse<LabTracker>> restartService(@PathVariable String labTrackerId,
                                                                  @PathVariable String service) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        currentUser.requireTrackerControl(labTracker);
        if (labTracker.getLabStarted().getLabType() != LabType.DOCKER_COMPOSE || labTracker.getRemoteServer() == null) {
            throw new IllegalArgumentException("Only running Docker Compose labs have services to restart");
        }
        if (!labActionGuard.tryAcquire(labTrackerId)) {
            throw new IllegalStateException("Another action is already running for this lab");
        }
        try {
            var result = dockerService.restartServices(labTracker, labTracker.getRemoteServer(), List.of(service));
            if (!result.isSuccess()) {
                throw new IllegalStateException("Could not restart " + service + ": " + result.getError());
            }
            return buildSuccessResponse("Restarted " + service, labTrackerService.refreshLabTracker(labTracker),
                    HttpStatus.OK);
        } finally {
            labActionGuard.release(labTrackerId);
        }
    }
}
