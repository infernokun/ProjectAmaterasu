package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a lab's environment from the lab page: the one shared copy, which its facilitators start and
 * stop, or the viewer's team's own copy.
 */
@Service
@RequiredArgsConstructor
public class LabEnvironmentService {
    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);

    private final LabTrackerService labTrackerService;
    private final LabService labService;
    private final RemoteServerService remoteServerService;
    private final CurrentUser currentUser;
    private final LabActionGuard labActionGuard;

    /** The copy the viewer works in: the lab's shared one, or their team's. */
    public Optional<LabTracker> copyFor(Room room, User viewer) {
        Lab environment = room.getEnvironment();
        return switch (room.effectiveMode()) {
            case NONE -> Optional.empty();
            case SHARED -> labTrackerService.findSharedCopy(room.getId(), environment);
            case PER_TEAM -> viewer == null || viewer.getTeam() == null ? Optional.empty()
                    : labTrackerService.findTeamCopy(environment, viewer.getTeam());
        };
    }

    /** Null for a lab without an environment. */
    public EnvironmentView view(Room room, User viewer, boolean canRun) {
        EnvironmentMode mode = room.effectiveMode();
        if (mode == EnvironmentMode.NONE) {
            return null;
        }
        Lab environment = room.getEnvironment();
        LabTracker tracker = copyFor(room, viewer).map(this::withPending).orElse(null);
        boolean canControl;
        String note = null;
        if (!LabService.isPublished(environment)) {
            canControl = false;
            note = environment.getName() + " is still a draft. Its authors publish it before it can start.";
        } else if (mode == EnvironmentMode.SHARED) {
            canControl = canRun;
            if (!canControl) {
                note = "A facilitator starts and stops this environment for everyone.";
            }
        } else {
            canControl = viewer != null && viewer.getTeam() != null;
            if (!canControl) {
                note = "Join a team to start your team's copy.";
            }
        }
        int running = mode == EnvironmentMode.SHARED ? (EnvironmentStatuses.isUp(tracker) ? 1 : 0)
                : (int) labTrackerService.findByLabStarted(environment).stream()
                .filter(copy -> !copy.isShared() && EnvironmentStatuses.isUp(copy)).count();
        return new EnvironmentView(environment.getId(), environment.getName(), environment.getLabType(), mode,
                EnvironmentStatuses.of(tracker), tracker != null ? tracker.getId() : null, tracker, canControl, note,
                running);
    }

    public LabTracker withPending(LabTracker tracker) {
        tracker.setPendingAction(labActionGuard.actionFor(tracker.getId()));
        return tracker;
    }

    /** Starts, or resumes, the viewer's copy. A first start creates it. */
    public LabActionResult start(Room room, String remoteServerId) {
        Lab environment = requireEnvironment(room);
        User user = currentUser.user();
        LabTracker tracker = copyFor(room, user).orElse(null);
        boolean shared = room.effectiveMode() == EnvironmentMode.SHARED;
        if (shared) {
            currentUser.requireCanRun(room);
        } else if (user.getTeam() == null) {
            throw new IllegalStateException("Join a team to start your team's copy of " + room.getName());
        }
        RemoteServer server = serverFor(tracker, environment, remoteServerId);
        if (server == null) {
            return new LabActionResult(false, tracker, "No " + serverKind(environment) + " is online and taking new labs for "
                    + environment.getName() + ". Ask an admin to check the servers.");
        }
        if (tracker == null) {
            tracker = labTrackerService.createLabTracker(LabTracker.builder()
                    .labStarted(environment)
                    .labOwner(shared ? null : user.getTeam())
                    .roomId(shared ? room.getId() : null)
                    .labStatus(LabStatus.NONE)
                    .build());
        }
        return labService.startLab(environment.getId(), user.getId(), tracker.getId(), server);
    }

    public LabActionResult pause(Room room) {
        LabTracker tracker = requireCopy(room);
        return labService.pauseLab(tracker.getLabStarted().getId(), currentUser.id(), tracker.getId(), null);
    }

    public LabActionResult stop(Room room) {
        LabTracker tracker = requireCopy(room);
        return labService.stopLab(tracker.getLabStarted().getId(), currentUser.id(), tracker.getId(), null);
    }

    private LabTracker requireCopy(Room room) {
        requireEnvironment(room);
        return copyFor(room, currentUser.user())
                .orElseThrow(() -> new ResourceNotFoundException(room.getName() + " has no running environment"));
    }

    private static Lab requireEnvironment(Room room) {
        if (room.effectiveMode() == EnvironmentMode.NONE) {
            throw new IllegalArgumentException(room.getName() + " has no environment to run");
        }
        return room.getEnvironment();
    }

    /** The server it is deployed on, the one asked for, or the online one with the most free memory. */
    private RemoteServer serverFor(LabTracker tracker, Lab environment, String requested) {
        if (tracker != null && tracker.getRemoteServer() != null && tracker.getLabStatus() != null
                && !NOT_DEPLOYED.contains(tracker.getLabStatus())) {
            return tracker.getRemoteServer();
        }
        if (requested != null && !requested.isBlank()) {
            return remoteServerService.findServerById(requested);
        }
        ServerType type = environment.getLabType() == LabType.VIRTUAL_MACHINE ? ServerType.PROXMOX : ServerType.DOCKER_HOST;
        return remoteServerService.findByServerType(type).stream()
                .filter(server -> server.getRemoteServerStats() != null
                        && server.getRemoteServerStats().getStatus() == LabStatus.ACTIVE)
                .filter(server -> !RemoteServerService.takesNoNewLabs(server))
                .max(Comparator.comparingDouble(server -> server.getRemoteServerStats().getAvailableRam()))
                .orElse(null);
    }

    private static String serverKind(Lab environment) {
        return environment.getLabType() == LabType.VIRTUAL_MACHINE ? "Proxmox node" : "Docker host";
    }
}
