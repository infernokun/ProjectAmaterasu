package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.infernokun.amaterasu.models.enums.LabStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab_tracker")
        //, uniqueConstraints = {@UniqueConstraint(columnNames = {"lab_id", "team_id"})})
public class LabTracker extends StoredObject {
    @ManyToOne
    @JoinColumn(name = "lab_id")
    private Lab labStarted;
    private LabStatus labStatus;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team labOwner;
}
