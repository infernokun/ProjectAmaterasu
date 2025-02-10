package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.helper.DockerServiceInfoConverter;
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
    private LabStatus labStatus;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team labOwner;
    @Convert(converter = DockerServiceInfoConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private List<DockerServiceInfo> services = new ArrayList<>();
}
