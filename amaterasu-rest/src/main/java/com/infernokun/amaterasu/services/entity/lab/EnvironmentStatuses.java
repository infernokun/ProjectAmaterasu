package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;

import java.util.List;
import java.util.Set;

/** One status for a copy, the same rule the web app applies to live updates. */
public final class EnvironmentStatuses {
    private static final Set<LabStatus> UP = Set.of(LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED,
            LabStatus.FAILED_ACTIVE);

    private EnvironmentStatuses() {
    }

    public static EnvironmentStatus of(LabTracker tracker) {
        if (tracker == null) {
            return EnvironmentStatus.NOT_RUNNING;
        }
        String pending = tracker.getPendingAction();
        if (LabActionGuard.STARTING.equals(pending) || LabActionGuard.RESUMING.equals(pending)) {
            return EnvironmentStatus.STARTING;
        }
        LabStatus status = tracker.getLabStatus();
        if (status != null && UP.contains(status)) {
            return startingUp(tracker.getVms()) ? EnvironmentStatus.STARTING : EnvironmentStatus.RUNNING;
        }
        if (status == LabStatus.PAUSED) {
            return EnvironmentStatus.PAUSED;
        }
        if (status == LabStatus.FAILED) {
            return EnvironmentStatus.FAILED;
        }
        return EnvironmentStatus.NOT_RUNNING;
    }

    /** A running VM whose guest agent has not answered yet. */
    static boolean startingUp(List<ProxmoxVM> vms) {
        return vms != null && vms.stream().anyMatch(vm -> "running".equals(vm.getStatus()) && vm.isGuestAgent()
                && Boolean.FALSE.equals(vm.getReady()));
    }

    public static boolean isUp(LabTracker tracker) {
        return tracker != null && tracker.getLabStatus() != null && UP.contains(tracker.getLabStatus());
    }
}
