package com.infernokun.amaterasu.services.cron;

import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProxmoxLabStatsService extends BaseService {
    private final ProxmoxService proxmoxService;
    private final LabTrackerService labTrackerService;
    private final LabActionGuard labActionGuard;

    public ProxmoxLabStatsService(ProxmoxService proxmoxService, LabTrackerService labTrackerService,
                                  LabActionGuard labActionGuard) {
        this.proxmoxService = proxmoxService;
        this.labTrackerService = labTrackerService;
        this.labActionGuard = labActionGuard;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void updateActiveProxmoxLabStats() {
        List<LabTracker> labTrackers = labTrackerService.findAllLabTrackers().stream().filter(
                labTracker -> labTracker.getLabStatus() == LabStatus.ACTIVE).toList();

        labTrackers.forEach(labTracker -> {
            // Only poll VM labs on Proxmox servers
            if (labTracker.getLabStarted().getLabType() != LabType.VIRTUAL_MACHINE || labTracker.getRemoteServer() == null
                    || labTracker.getRemoteServer().getServerType() != ServerType.PROXMOX) return;
            if (labActionGuard.isBusy(labTracker.getId())) return;
            try {
                List<ProxmoxVM> vms = proxmoxService.getVMsByIds(
                        labTracker.getRemoteServer(),
                        labTracker.getVms().stream().map(ProxmoxVM::getVmid).collect(Collectors.toList()));
                boolean missing = vms.size() != labTracker.getVms().size();
                if (missing) {
                    LOGGER.warn("Lab tracker {} is missing {} VM(s)", labTracker.getId(), labTracker.getVms().size() - vms.size());
                }
                // Present VMs also get their guest agent readiness and a bastion's address
                labTrackerService.updateVmsIfStatus(labTracker,
                        missing ? labTracker.getVms() : proxmoxService.refreshLabVMs(labTracker.getRemoteServer(), labTracker.getVms(), vms),
                        missing ? LabStatus.DEGRADED : LabStatus.ACTIVE, LabStatus.ACTIVE);
            } catch (Exception e) {
                // Unreadable is not gone: the lab keeps its VMs and status until the node answers
                LOGGER.warn("Could not refresh VMs for lab tracker {}: {}", labTracker.getId(), e.getMessage());
            }
        });
    }
}
