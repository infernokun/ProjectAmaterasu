package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.enums.LabStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTracker extends StoredObject {
    @ManyToOne
    @JoinColumn(name = "lab_id")
    private Lab labStarted;
    private LabStatus labStatus;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team labOwner;
}
