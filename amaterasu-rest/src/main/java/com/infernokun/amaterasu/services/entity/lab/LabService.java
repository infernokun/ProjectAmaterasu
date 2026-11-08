package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.lab.dto.LabRequest;
import com.infernokun.amaterasu.models.entities.*;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabFileChangeLog;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.lab.LabFileChangeLogRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.alt.LabActionService;
import com.infernokun.amaterasu.services.alt.LabReadinessService;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.TeamService;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.entity.ctf.CTFEntityService;
import com.infernokun.amaterasu.websocket.LabTrackerBusyEvent;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LabService extends BaseService {
    private final LabRepository labRepository;

    private final UserService userService;
    private final TeamService teamService;
    private final LabTrackerService labTrackerService;
    private final LabActionService labActionService;
    private final LabFileUploadService labFileUploadService;
    private final LabReadinessService labReadinessService;
    private final LabFileChangeLogRepository labFileChangeLogRepository;

    private final AmaterasuConfig amaterasuConfig;
    private final LabActionGuard labActionGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final CTFEntityService ctfEntityService;
    private final ApplicationEventPublisher events;
    private final RoomRepository roomRepository;

    public LabService(
            LabRepository labRepository, UserService userService,
            TeamService teamService, LabTrackerService labTrackerService,
            LabActionService labActionService, LabFileUploadService labFileUploadService,
            LabReadinessService labReadinessService, LabFileChangeLogRepository labFileChangeLogRepository,
            AmaterasuConfig amaterasuConfig, LabActionGuard labActionGuard, CurrentUser currentUser,
            AuditService auditService, CTFEntityService ctfEntityService, ApplicationEventPublisher events,
            RoomRepository roomRepository) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.labTrackerService = labTrackerService;
        this.labActionService = labActionService;
        this.labFileUploadService = labFileUploadService;
        this.labReadinessService = labReadinessService;
        this.labFileChangeLogRepository = labFileChangeLogRepository;
        this.amaterasuConfig = amaterasuConfig;
        this.labActionGuard = labActionGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.ctfEntityService = ctfEntityService;
        this.events = events;
        this.roomRepository = roomRepository;
    }

    static String composeFileSlug(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "lab" : slug;
    }

    public List<Lab> findAllLabs() {
        return labRepository.findAll();
    }

    public List<Lab> findPublishedLabs() {
        return labRepository.findAll().stream().filter(LabService::isPublished).toList();
    }

    public static boolean isPublished(Lab lab) {
        return Boolean.TRUE.equals(lab.getPublished());
    }

    /**
     * Makes a draft visible to teams. Only a lab that passed its checks can be published: a compose
     * file that a Docker host accepted, or VM templates.
     */
    public Lab publish(String id) {
        Lab lab = findLabById(id);
        if (isPublished(lab)) {
            return lab;
        }
        if (!lab.isReady()) {
            throw new IllegalStateException(lab.getName() + " is not ready yet. Validate it on a server first; "
                    + "compose labs turn ready once a Docker host accepts the file.");
        }
        lab.setPublished(true);
        lab.setUpdatedAt(LocalDateTime.now());
        Lab saved = labRepository.save(lab);
        auditService.record("LAB_PUBLISHED", "LAB", saved.getId(), saved.getName(), null, null);
        return saved;
    }

    /**
     * Turns a lab back into a draft. Refused while any team has it deployed, so no team loses a running
     * copy; teams that stopped it keep it in their list but cannot start it until it is published again.
     */
    public Lab unpublish(String id) {
        Lab lab = findLabById(id);
        if (!isPublished(lab)) {
            return lab;
        }
        long deployed = labTrackerService.findByLabStarted(lab).stream().filter(LabService::hasDeployment).count();
        if (deployed > 0) {
            throw new ResourceInUseException(lab.getName() + " is deployed by " + deployed
                    + (deployed == 1 ? " team" : " teams") + ". They need to stop it before it goes back to draft.");
        }
        lab.setPublished(false);
        lab.setUpdatedAt(LocalDateTime.now());
        Lab saved = labRepository.save(lab);
        auditService.record("LAB_UNPUBLISHED", "LAB", saved.getId(), saved.getName(), null, "Back to draft");
        return saved;
    }

    public Lab findLabById(String id) {
        return labRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab " + id + " not found"));
    }

    public List<Lab> findByLabType(LabType labType) {
        return labRepository.findByLabType(labType);
    }

    public Lab createLab(Lab lab) {
        // Format the current date-time for a cleaner string (e.g., "2025-01-25T01:51:23")
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Construct the dockerFile name with cleaned-up name and timestamp
        lab.setDockerFile(composeFileSlug(lab.getName()) + "_" + timestamp + ".yml");
        lab.setReady(false);

        // Save the lab with the updated dockerFile name
        return labRepository.save(lab);
    }

    public Lab createLab(LabRequest labRequest, RemoteServer remoteServer) {
        requireValidLab(labRequest);
        Lab created = doCreateLab(labRequest, remoteServer);
        auditService.record("LAB_CREATED", "LAB", created.getId(), created.getName(), null,
                created.getLabType() + (remoteServer != null ? " on " + remoteServer.getName() : ""));
        return created;
    }

    private static void requireValidLab(LabRequest labRequest) {
        if (labRequest.getLabType() == null) {
            throw new IllegalArgumentException("Choose Docker Compose or Proxmox VM");
        }
        if (labRequest.getName() == null || labRequest.getName().isBlank()) {
            throw new IllegalArgumentException("Lab name required");
        }
        if (labRequest.getCapacity() < 1) {
            throw new IllegalArgumentException("A lab needs room for at least one team copy");
        }
        if (labRequest.getLabType() == LabType.VIRTUAL_MACHINE
                && (labRequest.getVms() == null || labRequest.getVms().isEmpty())) {
            throw new IllegalArgumentException("Pick at least one VM template");
        }
        if (labRequest.getLabType() == LabType.DOCKER_COMPOSE
                && (labRequest.getDockerFile() == null || labRequest.getDockerFile().isBlank())) {
            throw new IllegalArgumentException("The compose file is empty");
        }
    }

    /** Edits a lab's details. Compose files change through uploadLabFile. */
    public Lab editLab(String id, LabRequest changes) {
        Lab lab = findLabById(id);
        if (changes.getName() != null) {
            if (changes.getName().isBlank()) {
                throw new IllegalArgumentException("Lab name required");
            }
            lab.setName(changes.getName().trim());
        }
        if (changes.getDescription() != null) {
            lab.setDescription(changes.getDescription());
        }
        if (changes.getVersion() != null) {
            lab.setVersion(changes.getVersion());
        }
        if (changes.getCapacity() > 0) {
            lab.setCapacity(changes.getCapacity());
        }
        if (lab.getLabType() == LabType.VIRTUAL_MACHINE && changes.getVms() != null && !changes.getVms().isEmpty()) {
            lab.setVmIds(new ArrayList<>(changes.getVms()));
        }
        lab.setUpdatedAt(LocalDateTime.now());
        Lab saved = labRepository.save(lab);
        auditService.record("LAB_UPDATED", "LAB", saved.getId(), saved.getName(), null, null);
        return saved;
    }

    private Lab doCreateLab(LabRequest labRequest, RemoteServer remoteServer) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        Lab newLab = null;

        switch (labRequest.getLabType()) {
            case DOCKER_COMPOSE -> {

                newLab = Lab.builder()
                        .name(labRequest.getName())
                        .description(labRequest.getDescription())
                        .labType(labRequest.getLabType())
                        .ready(false)
                        .dockerFile(composeFileSlug(labRequest.getName()) + "_" + timestamp + ".yml")
                        .version(labRequest.getVersion())
                        .capacity(labRequest.getCapacity())
                        .build();
                newLab.setCreatedBy(labRequest.getCreatedBy());
                Lab savedLab = labRepository.save(newLab);
                uploadLabFile(savedLab.getId(), labRequest.getDockerFile(), remoteServer);

                LabFileChangeLog labFileChangeLog = new LabFileChangeLog(savedLab);
                labFileChangeLog.setUpdatedAt(LocalDateTime.now().minusYears(10));
                labFileChangeLogRepository.save(labFileChangeLog);

                return savedLab;
            }
            case KUBERNETES -> throw new RuntimeException("Kube! Coming one day....");
            case VIRTUAL_MACHINE -> {
                newLab = Lab.builder()
                        .name(labRequest.getName())
                        .description(labRequest.getDescription())
                        .labType(labRequest.getLabType())
                        .ready(true)
                        .version(labRequest.getVersion())
                        .capacity(labRequest.getCapacity())
                        .vmIds(labRequest.getVms())
                        .status(LabStatus.ACTIVE)
                        .build();
                newLab.setCreatedBy(labRequest.getCreatedBy());
                return labRepository.save(newLab);
            }
            case DOCKER_CONTAINER -> throw new RuntimeException("Docker Container! Coming one day....");
            default -> {
                throw new RuntimeException("Coming one day....");
            }
        }
    }

    public List<Lab> createManyLabs(List<Lab> labs) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        for (Lab lab : labs) {
            lab.setDockerFile(composeFileSlug(lab.getName()) + "_" + timestamp + ".yml");
            lab.setPublished(false);
        }
        return labRepository.saveAll(labs);
    }

    public Lab updateLab(Lab updatedLabData) {
        findLabById(updatedLabData.getId());
        updatedLabData.setUpdatedAt(LocalDateTime.now());
        return labRepository.save(updatedLabData);
    }

    public String uploadLabFile(String labId, String content, RemoteServer remoteServer) {
        Lab lab = findLabById(labId);

        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labFileUploadService.uploadDockerComposeFile(lab, content, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new FileUploadException("coming one day...");
            }
            default -> throw new FileUploadException("Lab type not implemented...");
        }
    }

    @Transactional(dontRollbackOn = ResourceNotFoundException.class)
    public LabActionResult startLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        String trackerKey = labTrackerId == null || labTrackerId.isBlank() ? null : labTrackerId;
        boolean paused = trackerKey != null && labTrackerService.findLabTrackerById(trackerKey)
                .map(tracker -> tracker.getLabStatus() == LabStatus.PAUSED).orElse(false);
        if (!labActionGuard.tryAcquire(trackerKey, paused ? LabActionGuard.RESUMING : LabActionGuard.STARTING)) {
            return new LabActionResult(false, null, "Another action is already running for this lab");
        }
        announceBusy(trackerKey);
        try {
            return audited("LAB_STARTED", "Start", doStartLab(labId, userId, labTrackerId, remoteServer), labTrackerId);
        } finally {
            labActionGuard.release(trackerKey);
            announceBusy(trackerKey);
        }
    }

    /** Tells open views a tracker's action started or ended; LabActionGuard holds which one. */
    private void announceBusy(String trackerId) {
        if (trackerId != null) {
            labTrackerService.findLabTrackerById(trackerId)
                    .ifPresent(tracker -> events.publishEvent(new LabTrackerBusyEvent(tracker)));
        }
    }

    private LabActionResult doStartLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("lab id {} user id {}, lab tracker id {}", labId, userId, labTrackerId);

        Lab lab = findLabById(labId);
        if (!isPublished(lab)) {
            return new LabActionResult(false, null, lab.getName() + " is a draft. Its authors need to publish it before teams can start it.");
        }
        User user = this.userService.findUserById(userId);

        // A lab started from the catalog has no tracker yet
        Optional<LabTracker> existing = (labTrackerId == null || labTrackerId.isBlank()
                ? Optional.<LabTracker>empty() : labTrackerService.findLabTrackerById(labTrackerId))
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED);
        existing.ifPresent(currentUser::requireTrackerControl);
        // Only admins start a new copy straight from the catalog
        if (existing.isEmpty() && !currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admins start a new copy of " + lab.getName());
        }
        // A shared copy belongs to its lab and the solo copy to players without a team, not a team
        boolean ownerless = existing.map(tracker -> tracker.isShared() || tracker.isSolo()).orElse(false);
        Team userTeam = ownerless ? null : existing.map(LabTracker::getLabOwner).orElse(user.getTeam());
        if (!ownerless && userTeam == null) {
            return new LabActionResult(false, null, "Pick a team to start this copy for");
        }
        LabTracker labTracker = existing.orElseGet(() -> LabTracker.builder()
                .labStarted(lab)
                .labOwner(userTeam)
                .build());

        // A lab that still has containers or VMs must be resumed where it lives
        if (labTracker.getRemoteServer() != null && hasDeployment(labTracker)
                && !labTracker.getRemoteServer().getId().equals(remoteServer.getId())) {
            return new LabActionResult(false, labTracker, "Lab is deployed on " + labTracker.getRemoteServer().getName()
                    + ". Resume it there or stop it before moving to " + remoteServer.getName() + ".");
        }
        RemoteServer server = hasDeployment(labTracker) ? LabTrackerService.serverFor(labTracker, remoteServer) : remoteServer;

        // Resuming a paused lab is not a new deployment, so it still works on a server taking no new labs
        if (!hasDeployment(labTracker) && RemoteServerService.takesNoNewLabs(server)) {
            return new LabActionResult(false, labTracker, noNewLabsMessage(server));
        }

        if (server.getRemoteServerStats() == null || server.getRemoteServerStats().getStatus() != LabStatus.ACTIVE) {
            return new LabActionResult(false, labTracker, server.getName() + " is not ACTIVE!");
        }

        if (!usesCapacity(labTracker) && lab.getCapacity() != null && lab.getCapacity() > 0) {
            long inUse = labTrackerService.findByLabStarted(lab).stream().filter(LabService::usesCapacity).count();
            if (inUse >= lab.getCapacity()) {
                return new LabActionResult(false, labTracker,
                        lab.getName() + " is at capacity (" + lab.getCapacity() + " running)");
            }
        }

        labTracker.setRemoteServer(server);
        if (labTracker.getId() == null) {
            labTracker = labTrackerService.createLabTracker(labTracker);
        }

        LabActionResult labActionResult = labActionService.startLab(labTracker, server);
        if (labActionResult.getLabTracker() == null) {
            labActionResult.setLabTracker(labTracker);
        }

        LabTracker resultTracker = labActionResult.getLabTracker();
        resultTracker.setUpdatedBy(user.getId());
        resultTracker.setLabStatus(labActionResult.isSuccessful() ? LabStatus.ACTIVE : LabStatus.FAILED);

        if (resultTracker.getLabStatus() == LabStatus.ACTIVE && resultTracker.getServices() != null
                && resultTracker.getServices().stream().anyMatch(service -> !"running".equals(service.getState()))) {
            resultTracker.setLabStatus(LabStatus.DEGRADED);
        }

        try {
            labTrackerService.updateLabTracker(resultTracker);
        } catch (Exception e) {
            labActionResult.setSuccessful(false);
            return labActionResult;
        }

        if (userTeam != null && !userTeam.getTeamActiveLabs().contains(resultTracker.getId())) {
            userTeam.getTeamActiveLabs().add(resultTracker.getId());
            try {
                teamService.updateTeam(userTeam);
            } catch (Exception e) {
                labActionResult.setSuccessful(false);
                return labActionResult;
            }
        }
        return labActionResult;
    }

    /** Brings the lab down but keeps its containers or VM disks. */
    @Transactional
    public LabActionResult pauseLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        String trackerKey = labTrackerId == null || labTrackerId.isBlank() ? null : labTrackerId;
        if (!labActionGuard.tryAcquire(trackerKey, LabActionGuard.PAUSING)) {
            return new LabActionResult(false, null, "Another action is already running for this lab");
        }
        announceBusy(trackerKey);
        try {
            return audited("LAB_PAUSED", "Pause", doPauseLab(labId, userId, labTrackerId, remoteServer), labTrackerId);
        } finally {
            labActionGuard.release(trackerKey);
            announceBusy(trackerKey);
        }
    }

    private LabActionResult doPauseLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("Pausing lab {} by user {} with lab tracker {}", labId, userId, labTrackerId);
        LabTracker labTracker = activeTracker(labTrackerId);
        if (labTracker == null) {
            return new LabActionResult(false, null, "Lab tracker not found");
        }
        RemoteServer server = LabTrackerService.serverFor(labTracker, remoteServer);
        if (server == null) {
            return new LabActionResult(false, labTracker, "This lab is not deployed on a server, so there is nothing to pause");
        }
        LabActionResult result = labActionService.pauseLab(labTracker, server);
        return saveResult(result, labTracker, userId, LabStatus.PAUSED);
    }

    /** Removes the lab's containers or VMs. The tracker stays with the team and the next start is a fresh copy. */
    @Transactional
    public LabActionResult stopLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        String trackerKey = labTrackerId == null || labTrackerId.isBlank() ? null : labTrackerId;
        if (!labActionGuard.tryAcquire(trackerKey, LabActionGuard.STOPPING)) {
            return new LabActionResult(false, null, "Another action is already running for this lab");
        }
        announceBusy(trackerKey);
        try {
            return audited("LAB_STOPPED", "Stop", doStopLab(labId, userId, labTrackerId, remoteServer), labTrackerId);
        } finally {
            labActionGuard.release(trackerKey);
            announceBusy(trackerKey);
        }
    }

    private LabActionResult doStopLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("Stopping lab {} by user {} with lab tracker {}", labId, userId, labTrackerId);
        LabTracker labTracker = activeTracker(labTrackerId);
        if (labTracker == null) {
            return new LabActionResult(false, null, "Lab tracker not found");
        }
        RemoteServer server = LabTrackerService.serverFor(labTracker, remoteServer);
        // Nothing can be deployed without a server, so there is nothing to tear down
        LabActionResult result = server == null
                ? new LabActionResult(true, labTracker, "Nothing was deployed")
                : labActionService.teardownLab(labTracker, server);
        return saveResult(result, labTracker, userId, LabStatus.STOPPED);
    }

    /** Tears the lab down and removes it from the team. */
    @Transactional
    public LabActionResult deleteLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        String trackerKey = labTrackerId == null || labTrackerId.isBlank() ? null : labTrackerId;
        if (!labActionGuard.tryAcquire(trackerKey, LabActionGuard.REMOVING)) {
            return new LabActionResult(false, null, "Another action is already running for this lab");
        }
        announceBusy(trackerKey);
        try {
            return audited("LAB_DELETED", "Delete", doDeleteLab(labId, userId, labTrackerId, remoteServer), labTrackerId);
        } finally {
            labActionGuard.release(trackerKey);
            announceBusy(trackerKey);
        }
    }

    private LabActionResult doDeleteLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("Deleting lab {} by user {} with lab tracker {}", labId, userId, labTrackerId);
        LabTracker labTracker = activeTracker(labTrackerId);
        if (labTracker == null) {
            return new LabActionResult(false, null, "Lab tracker not found");
        }
        Team team = labTracker.getLabOwner() != null ? labTracker.getLabOwner() : userService.findUserById(userId).getTeam();
        if (team == null) {
            return new LabActionResult(false, labTracker, "Lab has no team");
        }

        RemoteServer server = LabTrackerService.serverFor(labTracker, remoteServer);
        LabActionResult result = server == null
                ? new LabActionResult(true, labTracker, "Nothing was deployed")
                : labActionService.teardownLab(labTracker, server);
        if (result.getLabTracker() == null) {
            result.setLabTracker(labTracker);
        }
        if (!result.isSuccessful()) {
            labTracker.setLabStatus(LabStatus.FAILED);
            labTrackerService.updateLabTracker(labTracker);
            LOGGER.warn("Lab deletion failed for lab {}: {}", labId, result.getOutput());
            return result;
        }

        markDeleted(labTracker, team, userId);
        return result;
    }

    /**
     * Removes a lab from the team. Anything still deployed is torn down first so nothing is left
     * running without a tracker.
     */
    @Transactional
    public LabTracker deleteLabFromTeam(String labId, String userId, String labTrackerId) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found!"));
        // Admins remove any team's copy, so the team is the copy's, not the caller's
        Team userTeam = labTracker.getLabOwner();
        if (userTeam == null) {
            throw new ResourceNotFoundException("Lab not found in a team!");
        }

        if (hasDeployment(labTracker) && labTracker.getRemoteServer() != null) {
            LabActionResult result = labActionService.teardownLab(labTracker, labTracker.getRemoteServer());
            if (!result.isSuccessful()) {
                throw new IllegalStateException("Could not tear down the lab before removing it: " + result.getOutput());
            }
        }

        LabTracker removed = markDeleted(labTracker, userTeam, userId);
        auditService.record("LAB_DELETED", "LAB_TRACKER", removed.getId(), labName(removed), userTeam.getId(),
                "Removed from the team");
        return removed;
    }

    /**
     * Moves a team's lab to another server. A lab that is not deployed only changes where it starts
     * next. A deployed one is torn down where it is and started fresh on the new server, so the
     * caller has to confirm with startFresh; its current containers or VMs and their data are lost.
     */
    @Transactional
    public LabActionResult moveLab(String labTrackerId, RemoteServer target, boolean startFresh) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Lab tracker not found"));
        RemoteServer source = labTracker.getRemoteServer();
        String name = labName(labTracker);
        String teamId = labTracker.getLabOwner() != null ? labTracker.getLabOwner().getId() : null;

        if (source != null && source.getId().equals(target.getId())) {
            throw new IllegalArgumentException(name + " is already on " + target.getName());
        }
        LabType type = labTracker.getLabStarted() != null ? labTracker.getLabStarted().getLabType() : null;
        if (type == LabType.DOCKER_COMPOSE && target.getServerType() != ServerType.DOCKER_HOST
                || type == LabType.VIRTUAL_MACHINE && target.getServerType() != ServerType.PROXMOX) {
            throw new IllegalArgumentException(target.getName() + " cannot run this kind of lab. Pick a "
                    + (type == LabType.VIRTUAL_MACHINE ? "Proxmox node." : "Docker host."));
        }
        if (RemoteServerService.takesNoNewLabs(target)) {
            throw new IllegalStateException(noNewLabsMessage(target));
        }
        boolean deployed = hasDeployment(labTracker);
        if (deployed && !startFresh) {
            throw new ResourceInUseException(name + " is deployed on " + (source != null ? source.getName() : "its server")
                    + ". Moving it destroys that copy and starts a fresh one on " + target.getName() + ".");
        }
        if (!labActionGuard.tryAcquire(labTracker.getId())) {
            throw new ResourceInUseException("Another action is already running for this lab");
        }
        try {
            String from = source != null ? source.getName() : "no server";
            if (!deployed) {
                labTracker.setRemoteServer(target);
                LabTracker saved = labTrackerService.updateLabTracker(labTracker);
                auditService.record("LAB_MOVED", "LAB_TRACKER", saved.getId(), name, teamId,
                        "From " + from + " to " + target.getName() + "; starts there next time");
                return new LabActionResult(true, saved, name + " will start on " + target.getName());
            }

            if (target.getRemoteServerStats() == null || target.getRemoteServerStats().getStatus() != LabStatus.ACTIVE) {
                throw new IllegalStateException(target.getName() + " is not online. Check it, then try the move again.");
            }
            LabActionResult teardown = source == null
                    ? new LabActionResult(true, labTracker, "Nothing was deployed")
                    : labActionService.teardownLab(labTracker, source);
            if (!teardown.isSuccessful()) {
                String reason = teardown.getOutput() == null || teardown.getOutput().isBlank() ? "no output" : teardown.getOutput();
                auditService.record("LAB_ACTION_FAILED", "LAB_TRACKER", labTracker.getId(), name, teamId,
                        "Move to " + target.getName() + " failed: the copy on " + from + " could not be removed: " + reason);
                return new LabActionResult(false, labTracker, "The copy on " + from
                        + " could not be removed, so nothing was moved: " + reason);
            }

            LabTracker moved = teardown.getLabTracker() != null ? teardown.getLabTracker() : labTracker;
            moved.setRemoteServer(target);
            moved.setLabStatus(LabStatus.STOPPED);
            moved.setUpdatedBy(currentUser.id());
            moved = labTrackerService.updateLabTracker(moved);

            LabActionResult started = labActionService.startLab(moved, target);
            LabTracker result = started.getLabTracker() != null ? started.getLabTracker() : moved;
            result.setRemoteServer(target);
            result.setLabStatus(started.isSuccessful() ? LabStatus.ACTIVE : LabStatus.FAILED);
            if (started.isSuccessful() && result.getServices() != null
                    && result.getServices().stream().anyMatch(service -> !"running".equals(service.getState()))) {
                result.setLabStatus(LabStatus.DEGRADED);
            }
            started.setLabTracker(labTrackerService.updateLabTracker(result));
            if (started.isSuccessful()) {
                auditService.record("LAB_MOVED", "LAB_TRACKER", result.getId(), name, teamId,
                        "From " + from + " to " + target.getName() + "; the old copy was destroyed and a fresh one started");
            } else {
                String reason = started.getOutput() == null || started.getOutput().isBlank() ? "no output" : started.getOutput();
                auditService.record("LAB_ACTION_FAILED", "LAB_TRACKER", result.getId(), name, teamId,
                        "Moved from " + from + " to " + target.getName() + ", but the fresh copy did not start: " + reason);
                started.setOutput("The copy on " + from + " was removed, but the fresh copy did not start on "
                        + target.getName() + ": " + reason);
            }
            return started;
        } finally {
            labActionGuard.release(labTracker.getId());
        }
    }

    private static String noNewLabsMessage(RemoteServer server) {
        return server.getName() + " takes no new labs right now. Pick another server, or ask an admin to allow new labs on it.";
    }

    /** Records a lab action: the action on success, LAB_ACTION_FAILED with the reason otherwise. */
    private LabActionResult audited(String action, String verb, LabActionResult result, String requestedTrackerId) {
        LabTracker tracker = result.getLabTracker();
        String trackerId = tracker != null && tracker.getId() != null ? tracker.getId() : requestedTrackerId;
        if (trackerId == null || trackerId.isBlank()) {
            return result;
        }
        String teamId = tracker != null && tracker.getLabOwner() != null ? tracker.getLabOwner().getId() : null;
        String server = tracker != null && tracker.getRemoteServer() != null ? tracker.getRemoteServer().getName() : null;
        if (result.isSuccessful()) {
            auditService.record(action, "LAB_TRACKER", trackerId, labName(tracker), teamId,
                    server != null ? "On " + server : null);
        } else {
            String reason = result.getOutput() == null || result.getOutput().isBlank() ? "no output" : result.getOutput();
            auditService.record("LAB_ACTION_FAILED", "LAB_TRACKER", trackerId, labName(tracker), teamId,
                    verb + " failed" + (server != null ? " on " + server : "") + ": " + reason);
        }
        return result;
    }

    private static String labName(LabTracker tracker) {
        return tracker != null && tracker.getLabStarted() != null ? tracker.getLabStarted().getName() : null;
    }

    private LabTracker markDeleted(LabTracker labTracker, Team team, String userId) {
        labTracker.setLabStatus(LabStatus.DELETED);
        labTracker.setUpdatedBy(userId);
        team.getTeamActiveLabs().remove(labTracker.getId());
        if (!team.getTeamDeletedLabs().contains(labTracker.getId())) {
            team.getTeamDeletedLabs().add(labTracker.getId());
        }
        teamService.updateTeam(team);
        return labTrackerService.updateLabTracker(labTracker);
    }

    private LabTracker activeTracker(String labTrackerId) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED)
                .orElse(null);
        if (labTracker != null) {
            currentUser.requireTrackerControl(labTracker);
        }
        return labTracker;
    }

    private LabActionResult saveResult(LabActionResult result, LabTracker labTracker, String userId, LabStatus onSuccess) {
        if (result.getLabTracker() == null) {
            result.setLabTracker(labTracker);
        }
        LabTracker tracker = result.getLabTracker();
        tracker.setUpdatedBy(userId);
        tracker.setLabStatus(result.isSuccessful() ? onSuccess : LabStatus.FAILED);
        labTrackerService.updateLabTracker(tracker);
        return result;
    }

    /** True while the tracker still owns containers or VMs on its server. */
    private static boolean hasDeployment(LabTracker labTracker) {
        LabStatus status = labTracker.getLabStatus();
        return status != null && status != LabStatus.STOPPED && status != LabStatus.DELETED
                && status != LabStatus.NONE;
    }

    private static boolean usesCapacity(LabTracker labTracker) {
        LabStatus status = labTracker.getLabStatus();
        return status == LabStatus.ACTIVE || status == LabStatus.DEGRADED || status == LabStatus.PAUSED;
    }

    public boolean checkDockerComposeValidity(String labId, RemoteServer remoteServer) {
        Lab lab = findLabById(labId);
        try {
            switch (lab.getLabType()) {
                case DOCKER_COMPOSE -> {
                    return labReadinessService.checkDockerComposeReadiness(lab, remoteServer);
                }
                case DOCKER_CONTAINER -> {
                    throw new LabReadinessException("coming one day...");
                }
                default -> throw new LabReadinessException("Lab type not implemented...");
            }
        } catch (LabReadinessException e) {
            LOGGER.error(e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getLabFile(Lab lab, RemoteServer remoteServer) {
        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.getDockerComposeFile(lab, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public Map<String, Object> getLabConfiguration(LabTracker labTracker, RemoteServer remoteServer) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.getDockerComposeFile(labTracker, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    /**
     * Removes a lab from the catalog. Refused while any team has it deployed; stopped copies are
     * removed from their teams along with it.
     */
    public boolean deleteLabItem(String labId) {
        Optional<Lab> lab = labRepository.findById(labId);
        if (lab.isEmpty()) {
            return false;
        }
        List<LabTracker> trackers = labTrackerService.findByLabStarted(lab.get());
        long deployed = trackers.stream().filter(LabService::hasDeployment).count();
        if (deployed > 0) {
            throw new ResourceInUseException(lab.get().getName() + " is deployed by " + deployed
                    + (deployed == 1 ? " team" : " teams") + ". They need to stop it before it can be deleted.");
        }

        for (LabTracker tracker : trackers) {
            Team owner = tracker.getLabOwner();
            if (owner == null) {
                continue;
            }
            boolean listed = owner.getTeamActiveLabs().remove(tracker.getId());
            listed = owner.getTeamDeletedLabs().remove(tracker.getId()) || listed;
            if (listed) {
                teamService.updateTeam(owner);
            }
        }
        labTrackerService.deleteAll(trackers);
        ctfEntityService.unlinkLab(labId);
        // Labs that ran it keep their scenario and questions, without an environment
        for (Room room : roomRepository.findAll()) {
            if (labId.equals(room.getEnvironmentId())) {
                room.setEnvironment(null);
                room.setEnvironmentMode(EnvironmentMode.NONE);
                roomRepository.save(room);
            }
        }
        labFileChangeLogRepository.deleteByLabId(labId);
        labRepository.deleteById(labId);
        auditService.record("LAB_REMOVED", "LAB", labId, lab.get().getName(), null, null);
        return true;
    }

    public void clear(String teamId) {
        Team team = teamService.findTeamById(teamId);

        team.setTeamActiveLabs(new ArrayList<>());
        team.setTeamDeletedLabs(new ArrayList<>());

        teamService.updateTeam(team);
        labTrackerService.deleteAll(labTrackerService.findLabTrackerByTeamId(teamId));
    }
}
