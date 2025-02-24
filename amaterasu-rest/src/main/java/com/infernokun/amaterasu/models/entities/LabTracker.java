package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.ProxmoxVM;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.helper.DockerServiceInfoConverter;
import com.infernokun.amaterasu.models.helper.ProxmoxVMListConverter;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab_tracker")
public class LabTracker extends StoredObject {
    @ManyToOne
    @JoinColumn(name = "lab_id")
    private Lab labStarted;
    @Enumerated(EnumType.STRING)
    private LabStatus labStatus;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team labOwner;
    @Convert(converter = DockerServiceInfoConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private List<DockerServiceInfo> services = new ArrayList<>();
    @Column(columnDefinition = "TEXT")
    @Convert(converter = ProxmoxVMListConverter.class)
    @Builder.Default
    private List<ProxmoxVM> vms = new ArrayList<>();
    @ManyToOne
    @JoinColumn(name = "remote_server_id")
    private RemoteServer remoteServer;
}
