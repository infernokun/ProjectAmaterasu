package com.infernokun.amaterasu.models.entities;

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
    /** The lab (room) a team plays in; null for an organisation-wide team from Admin. */
    private String roomId;
    @Builder.Default
    private List<String> teamActiveLabs = new ArrayList<>();
    @Builder.Default
    private List<String> teamDeletedLabs = new ArrayList<>();
}
