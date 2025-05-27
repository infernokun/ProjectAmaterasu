package com.infernokun.amaterasu.services.cron;

import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.entity.LabTrackerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProxmoxLabStatsService extends BaseService {
    private final ProxmoxService proxmoxService;
    private final LabTrackerService labTrackerService;

    public ProxmoxLabStatsService(ProxmoxService proxmoxService, LabTrackerService labTrackerService) {
        this.proxmoxService = proxmoxService;
        this.labTrackerService = labTrackerService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void updateActiveProxmoxLabStats() {
        List<LabTracker> labTrackers = labTrackerService.findAllLabTrackers().stream().filter(
                labTracker -> labTracker.getLabStatus() == LabStatus.ACTIVE).toList();

        labTrackers.forEach(labTracker -> {
            if (labTracker.getLabStarted().getLabType() != LabType.VIRTUAL_MACHINE && labTracker.getRemoteServer().getServerType() != ServerType.PROXMOX) return;
            List<ProxmoxVM> vms = proxmoxService.getVMsByIds(
                    labTracker.getRemoteServer(),
                    labTracker.getVms().stream().map(ProxmoxVM::getVmid).collect(Collectors.toList())
            );

            LOGGER.info("Lab Tracker {} w/ lab {} for {} updated!", labTracker.getId(), labTracker.getLabStarted().getName(), labTracker.getLabOwner().getName());
            labTracker.setVms(vms);

            labTrackerService.updateLabTracker(labTracker);
        });
    }
}
