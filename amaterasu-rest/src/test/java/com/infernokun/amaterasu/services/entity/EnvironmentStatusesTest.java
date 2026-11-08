package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.services.entity.lab.EnvironmentStatuses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentStatusesTest {
    private static LabTracker tracker(LabStatus status, String pending, ProxmoxVM... vms) {
        LabTracker tracker = LabTracker.builder().labStatus(status).vms(List.of(vms)).build();
        tracker.setPendingAction(pending);
        return tracker;
    }

    @Test
    void mapsEveryStoredStatusToOneOfFive() {
        assertThat(EnvironmentStatuses.of(null)).isEqualTo(EnvironmentStatus.NOT_RUNNING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.NONE, null))).isEqualTo(EnvironmentStatus.NOT_RUNNING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.STOPPED, null))).isEqualTo(EnvironmentStatus.NOT_RUNNING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.ACTIVE, null))).isEqualTo(EnvironmentStatus.RUNNING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.DEGRADED, null))).isEqualTo(EnvironmentStatus.RUNNING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.PAUSED, null))).isEqualTo(EnvironmentStatus.PAUSED);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.FAILED, null))).isEqualTo(EnvironmentStatus.FAILED);
    }

    @Test
    void aStartInProgressOrAVmStillBootingIsStarting() {
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.STOPPED, LabActionGuard.STARTING))).isEqualTo(EnvironmentStatus.STARTING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.PAUSED, LabActionGuard.RESUMING))).isEqualTo(EnvironmentStatus.STARTING);
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.ACTIVE, LabActionGuard.STOPPING))).isEqualTo(EnvironmentStatus.RUNNING);
        ProxmoxVM booting = ProxmoxVM.builder().vmid(1).status("running").guestAgent(true).ready(false).build();
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.ACTIVE, null, booting))).isEqualTo(EnvironmentStatus.STARTING);
        ProxmoxVM ready = ProxmoxVM.builder().vmid(1).status("running").guestAgent(true).ready(true).build();
        assertThat(EnvironmentStatuses.of(tracker(LabStatus.ACTIVE, null, ready))).isEqualTo(EnvironmentStatus.RUNNING);
    }
}
