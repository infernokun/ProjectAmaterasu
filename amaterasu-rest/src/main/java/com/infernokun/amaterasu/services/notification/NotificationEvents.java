package com.infernokun.amaterasu.services.notification;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.services.entity.AuditRecordedEvent;
import com.infernokun.amaterasu.websocket.LabTrackerChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for events and watches release times and server health, and hands what it sees to
 * {@link NotificationRules}. A failure here is logged and never reaches the action that caused it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEvents {
    private static final Set<LabStatus> BROKEN = Set.of(LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE);

    private final NotificationRules rules;

    private final Map<String, LabStatus> trackerStatus = new ConcurrentHashMap<>();
    private final Map<String, LabStatus> serverStatus = new ConcurrentHashMap<>();
    private volatile LocalDateTime releasesCheckedUntil = LocalDateTime.now();
    private volatile boolean serversSeen;

    @TransactionalEventListener(fallbackExecution = true)
    public void onAudit(AuditRecordedEvent recorded) {
        try {
            rules.onAudit(recorded.event());
        } catch (RuntimeException e) {
            log.warn("Could not notify about audit event {}", recorded.event().getAction(), e);
        }
    }

    /** Lab action failures come through the audit log; this catches labs that break on their own. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onLabTracker(LabTrackerChangedEvent event) {
        LabTracker tracker = event.labTracker();
        if (tracker == null || tracker.getId() == null) {
            return;
        }
        if (event.removed()) {
            trackerStatus.remove(tracker.getId());
            return;
        }
        LabStatus status = tracker.getLabStatus();
        LabStatus previous = status == null ? trackerStatus.remove(tracker.getId()) : trackerStatus.put(tracker.getId(), status);
        if (status != null && BROKEN.contains(status) && status != previous) {
            try {
                rules.onLabDegraded(tracker);
            } catch (RuntimeException e) {
                log.warn("Could not notify about lab tracker {}", tracker.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${NOTIFICATION_POLL_MS:30000}", initialDelayString = "${NOTIFICATION_POLL_MS:30000}")
    public void checkReleases() {
        LocalDateTime from = releasesCheckedUntil;
        LocalDateTime to = LocalDateTime.now();
        try {
            rules.releasedBetween(from, to);
            releasesCheckedUntil = to;
        } catch (RuntimeException e) {
            log.warn("Could not check challenge releases", e);
        }
    }

    /** The first look only records each server's state, so a restart does not announce every offline server. */
    @Scheduled(fixedDelayString = "${NOTIFICATION_POLL_MS:30000}", initialDelayString = "${NOTIFICATION_POLL_MS:30000}")
    public void checkServers() {
        try {
            Map<String, NotificationRules.ServerHealth> health = rules.serverHealth();
            serverStatus.keySet().retainAll(health.keySet());
            health.forEach((serverId, server) -> {
                if (server.status() == null) {
                    return;
                }
                LabStatus previous = serverStatus.put(serverId, server.status());
                if (!serversSeen || previous == null || previous == server.status()) {
                    return;
                }
                if (server.status() == LabStatus.OFFLINE) {
                    rules.serverChanged(serverId, server.name(), false);
                } else if (previous == LabStatus.OFFLINE && server.status() == LabStatus.ACTIVE) {
                    rules.serverChanged(serverId, server.name(), true);
                }
            });
            serversSeen = true;
        } catch (RuntimeException e) {
            log.warn("Could not check server health for notifications", e);
        }
    }
}
