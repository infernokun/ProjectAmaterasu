package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView.EnvironmentCopy;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs a lab's environment from the lab page. Admins and the lab's facilitators start and stop every
 * copy: the shared one, each team's, and the solo copy players without a team share. Players only see
 * the copy they work in.
 */
@Service
@RequiredArgsConstructor
public class LabEnvironmentService {
    public static final String SHARED = "shared";
    public static final String SOLO = "solo";
    public static final String TEAM = "team:";
    static final String NOT_RUNNING = "Not running yet: an admin starts this environment.";
    static final String PAUSED = "Paused: an admin resumes this environment.";
    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);

    private final LabTrackerService labTrackerService;
    private final LabService labService;
    private final RemoteServerService remoteServerService;
    private final CurrentUser currentUser;
    private final LabActionGuard labActionGuard;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    /** One copy of a lab's environment: the shared one, a team's, or the solo copy. */
    record Copy(String target, Team team, boolean solo, Optional<LabTracker> tracker) {
    }

    /** The copy the viewer works in: the lab's shared one, their team's, or the solo copy. */
    public Optional<LabTracker> copyFor(Room room, User viewer) {
        Lab environment = room.getEnvironment();
        return switch (room.effectiveMode()) {
            case NONE -> Optional.empty();
            case SHARED -> labTrackerService.findSharedCopy(room.getId(), environment);
            case PER_TEAM -> viewer != null && viewer.getTeam() != null
                    ? labTrackerService.findTeamCopy(environment, viewer.getTeam())
                    : labTrackerService.findSoloCopy(environment);
        };
    }

    /** Null for a lab without an environment. Those who run the lab also get every copy. */
    public EnvironmentView view(Room room, User viewer, boolean canRun) {
        return view(room, viewer, canRun, true);
    }

    public EnvironmentView view(Room room, User viewer, boolean canRun, boolean withCopies) {
        EnvironmentMode mode = room.effectiveMode();
        if (mode == EnvironmentMode.NONE) {
            return null;
        }
        Lab environment = room.getEnvironment();
        LabTracker tracker = copyFor(room, viewer).map(this::withPending).orElse(null);
        EnvironmentStatus status = EnvironmentStatuses.of(tracker);
        boolean published = LabService.isPublished(environment);
        String note = null;
        if (!published) {
            note = environment.getName() + " is still a draft. Its authors publish it before it can start.";
        } else if (!canRun && status == EnvironmentStatus.PAUSED) {
            note = PAUSED;
        } else if (!canRun && (status == EnvironmentStatus.NOT_RUNNING || status == EnvironmentStatus.FAILED)) {
            note = NOT_RUNNING;
        }
        Team team = viewer != null ? viewer.getTeam() : null;
        String copyLabel = mode == EnvironmentMode.SHARED ? "Shared copy"
                : team != null ? team.getName() + "'s copy" : "Solo copy";
        int running = mode == EnvironmentMode.SHARED ? (EnvironmentStatuses.isUp(tracker) ? 1 : 0)
                : (int) labTrackerService.findByLabStarted(environment).stream()
                .filter(copy -> !copy.isShared() && EnvironmentStatuses.isUp(copy))
                .count();
        List<EnvironmentCopy> copies = canRun && withCopies ? copies(room) : List.of();
        return new EnvironmentView(environment.getId(), environment.getName(), environment.getLabType(), mode,
                status, tracker != null ? tracker.getId() : null, tracker, copyLabel, canRun && published, note,
                running, copies);
    }

    /** Every copy of the lab's environment: the shared one, or one per team then the solo copy. */
    public List<EnvironmentCopy> copies(Room room) {
        Lab environment = room.getEnvironment();
        if (room.effectiveMode() == EnvironmentMode.SHARED) {
            Copy shared = new Copy(SHARED, null, false, labTrackerService.findSharedCopy(room.getId(), environment));
            return List.of(view(shared, "Shared copy", userRepository.count()));
        }
        if (room.effectiveMode() != EnvironmentMode.PER_TEAM) {
            return List.of();
        }
        Map<String, Long> members = userRepository.findAll().stream()
                .collect(Collectors.groupingBy(user -> user.getTeam() != null ? user.getTeam().getId() : SOLO,
                        Collectors.counting()));
        List<EnvironmentCopy> views = new ArrayList<>();
        for (Team team : teamRepository.findAllByOrderByNameAsc()) {
            Copy copy = new Copy(TEAM + team.getId(), team, false, labTrackerService.findTeamCopy(environment, team));
            views.add(view(copy, team.getName(), members.getOrDefault(team.getId(), 0L)));
        }
        Copy solo = new Copy(SOLO, null, true, labTrackerService.findSoloCopy(environment));
        views.add(view(solo, "Solo copy", members.getOrDefault(SOLO, 0L)));
        return views;
    }

    private EnvironmentCopy view(Copy copy, String label, long members) {
        LabTracker tracker = copy.tracker().map(this::withPending).orElse(null);
        return new EnvironmentCopy(copy.target(), label, copy.team() != null ? copy.team().getId() : null, copy.solo(),
                (int) members, EnvironmentStatuses.of(tracker), tracker != null ? tracker.getId() : null, tracker);
    }

    public LabTracker withPending(LabTracker tracker) {
        tracker.setPendingAction(labActionGuard.actionFor(tracker.getId()));
        return tracker;
    }

    /** Starts, or resumes, one copy. A first start creates it. */
    public LabActionResult start(Room room, String target, String remoteServerId) {
        Lab environment = requireEnvironment(room);
        currentUser.requireCanRun(room);
        Copy copy = resolve(room, target);
        LabTracker tracker = copy.tracker().orElse(null);
        RemoteServer server = serverFor(tracker, environment, remoteServerId);
        if (server == null) {
            return new LabActionResult(false, tracker, "No " + serverKind(environment) + " is online and taking new labs for "
                    + environment.getName() + ". Check the servers.");
        }
        if (tracker == null) {
            tracker = labTrackerService.createLabTracker(LabTracker.builder()
                    .labStarted(environment)
                    .labOwner(copy.team())
                    .roomId(SHARED.equals(copy.target()) ? room.getId() : null)
                    .solo(copy.solo() ? Boolean.TRUE : null)
                    .labStatus(LabStatus.NONE)
                    .build());
        }
        return labService.startLab(environment.getId(), currentUser.id(), tracker.getId(), server);
    }

    public LabActionResult pause(Room room, String target) {
        LabTracker tracker = requireCopy(room, target);
        return labService.pauseLab(tracker.getLabStarted().getId(), currentUser.id(), tracker.getId(), null);
    }

    public LabActionResult stop(Room room, String target) {
        LabTracker tracker = requireCopy(room, target);
        return labService.stopLab(tracker.getLabStarted().getId(), currentUser.id(), tracker.getId(), null);
    }

    private LabTracker requireCopy(Room room, String target) {
        requireEnvironment(room);
        currentUser.requireCanRun(room);
        return resolve(room, target).tracker()
                .orElseThrow(() -> new ResourceNotFoundException("That copy of " + room.getName() + " is not running"));
    }

    /** "shared" (the default for a shared environment), "solo", or "team:" and a team's id. */
    Copy resolve(Room room, String target) {
        Lab environment = room.getEnvironment();
        String key = target == null || target.isBlank() ? null : target.trim();
        if (room.effectiveMode() == EnvironmentMode.SHARED) {
            if (key != null && !SHARED.equals(key)) {
                throw new IllegalArgumentException(room.getName() + " runs one shared copy");
            }
            return new Copy(SHARED, null, false, labTrackerService.findSharedCopy(room.getId(), environment));
        }
        if (key == null) {
            throw new IllegalArgumentException("Pick a copy of " + room.getName() + ": a team's or the solo copy");
        }
        if (SOLO.equals(key)) {
            return new Copy(SOLO, null, true, labTrackerService.findSoloCopy(environment));
        }
        if (key.startsWith(TEAM)) {
            String teamId = key.substring(TEAM.length());
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team " + teamId + " not found"));
            return new Copy(key, team, false, labTrackerService.findTeamCopy(environment, team));
        }
        throw new IllegalArgumentException("Unknown copy " + key);
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
