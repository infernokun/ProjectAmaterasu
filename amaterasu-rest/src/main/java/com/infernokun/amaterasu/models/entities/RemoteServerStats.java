package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.enums.LabStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remote_server_stats")
public class RemoteServerStats extends StoredObject {
    private String hostname;
    private String osName;
    private String osVersion;
    private float totalRam;
    private float availableRam;
    private float usedRam;
    private int cpu;
    private double cpuUsagePercent;
    private float totalDiskSpace;
    private float availableDiskSpace;
    private float usedDiskSpace;
    private long uptime;
    private LabStatus status;
    @ManyToOne
    @JoinColumn(name = "remote_server_id")
    private RemoteServer remoteServer;
}
