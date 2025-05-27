package com.infernokun.amaterasu.models.entities.lab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.enums.LabStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remote_server_stats")
@ToString(exclude = "remoteServer")
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
    @Enumerated(EnumType.STRING)
    private LabStatus status;
    @ManyToOne
    @JoinColumn(name = "remote_server_id")
    @JsonIgnore
    private RemoteServer remoteServer;
}
