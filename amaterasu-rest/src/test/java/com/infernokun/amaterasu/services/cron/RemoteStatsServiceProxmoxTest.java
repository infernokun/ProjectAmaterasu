package com.infernokun.amaterasu.services.cron;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.lab.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.lab.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A Proxmox server's stats row holds what the node reported, from its very first check. */
class RemoteStatsServiceProxmoxTest {
    private ProxmoxService proxmox;
    private RemoteServerStatsRepository repository;
    private RemoteServerStatsService statsService;
    private RemoteStatsService remoteStats;
    private RemoteServer server;

    @BeforeEach
    void setUp() {
        proxmox = mock(ProxmoxService.class);
        repository = mock(RemoteServerStatsRepository.class);
        statsService = mock(RemoteServerStatsService.class);
        RemoteServerService servers = mock(RemoteServerService.class);
        remoteStats = new RemoteStatsService(servers, statsService, mock(RemoteCommandService.class), repository,
                new ObjectMapper(), mock(AmaterasuConfig.class), proxmox);
        server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.205").nodeName("proxmox2")
                .serverType(ServerType.PROXMOX).build();
        server.setId("server-1");
        when(statsService.createStats(any())).thenAnswer(call -> call.getArgument(0));
        when(servers.modifyStatus(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static RemoteServerStats reported() {
        return RemoteServerStats.builder().hostname("proxmox2").osName("Proxmox VE").osVersion("9.2.2").cpu(4)
                .cpuUsagePercent(0.2).totalRam(31.2f).usedRam(1.7f).availableRam(29.5f).totalDiskSpace(64.1f)
                .usedDiskSpace(4.1f).availableDiskSpace(56.6f).uptime(16532).status(LabStatus.ACTIVE).build();
    }

    @Test
    void aNewServersFirstCheckSavesWhatTheNodeReported() {
        when(repository.findByRemoteServerId("server-1")).thenReturn(Optional.empty());
        when(proxmox.nodeStats(server)).thenReturn(reported());

        remoteStats.refresh(server);

        ArgumentCaptor<RemoteServerStats> saved = ArgumentCaptor.forClass(RemoteServerStats.class);
        verify(statsService).createStats(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(LabStatus.ACTIVE);
        assertThat(saved.getValue().getTotalRam()).isEqualTo(31.2f);
        assertThat(saved.getValue().getOsVersion()).isEqualTo("9.2.2");
        assertThat(saved.getValue().getRemoteServer()).isSameAs(server);
    }

    @Test
    void aNewServerThatFailsItsFirstCheckIsOfflineWithTheReason() {
        when(repository.findByRemoteServerId("server-1")).thenReturn(Optional.empty());
        RemoteServerStats failed = RemoteServerStats.builder().hostname("proxmox2").osName("Proxmox VE")
                .status(LabStatus.OFFLINE).warning("No node called proxmox2 on that host").build();
        when(proxmox.nodeStats(server)).thenReturn(failed);

        remoteStats.refresh(server);

        ArgumentCaptor<RemoteServerStats> saved = ArgumentCaptor.forClass(RemoteServerStats.class);
        verify(statsService).createStats(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(LabStatus.OFFLINE);
        assertThat(saved.getValue().getWarning()).isEqualTo("No node called proxmox2 on that host");
    }

    @Test
    void laterChecksUpdateTheRowAndKeepTheLastNumbersWhenOffline() {
        RemoteServerStats row = RemoteServerStats.builder().status(LabStatus.OFFLINE).warning("old").build();
        row.setId("stats-1");
        when(repository.findByRemoteServerId("server-1")).thenReturn(Optional.of(row));
        when(proxmox.nodeStats(server)).thenReturn(reported());

        remoteStats.refresh(server);

        verify(statsService).updateStats(row);
        assertThat(row.getStatus()).isEqualTo(LabStatus.ACTIVE);
        assertThat(row.getWarning()).isNull();
        assertThat(row.getUsedDiskSpace()).isEqualTo(4.1f);
        assertThat(row.getUptime()).isEqualTo(16532L);

        RemoteServerStats offline = RemoteServerStats.builder().hostname("proxmox2").osName("Proxmox VE")
                .status(LabStatus.OFFLINE).warning("No answer from 10.0.0.205:8006. Check the address and that the node is up").build();
        when(proxmox.nodeStats(server)).thenReturn(offline);
        remoteStats.refresh(server);

        assertThat(row.getStatus()).isEqualTo(LabStatus.OFFLINE);
        assertThat(row.getWarning()).startsWith("No answer from");
        assertThat(row.getTotalRam()).isEqualTo(31.2f);
    }
}
