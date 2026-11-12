package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "team")
public class Team extends StoredObject {
    private String name;
    private String description;
    /** The lab (room) a lab team belongs to; null for a site team. */
    @Column(name = "scope_room_id")
    private String labId;
    @Builder.Default
    private List<String> teamActiveLabs = new ArrayList<>();
    @Builder.Default
    private List<String> teamDeletedLabs = new ArrayList<>();

    @JsonIgnore
    public boolean isLabTeam() {
        return labId != null;
    }
}
