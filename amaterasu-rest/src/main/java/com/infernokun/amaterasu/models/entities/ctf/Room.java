package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.utils.AmaterasuConstants;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A lab as players see it: a scenario, its questions and, optionally, an environment to work in.
 * Stored as a room; the environment is a lab definition.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room extends StoredObject {
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator")
    @JsonIgnore
    private User creator;
    @ElementCollection(fetch = FetchType.EAGER)
    @JsonIgnore
    @Builder.Default
    private List<String> facilitators = new ArrayList<>();
    @Builder.Default
    private String surroundTag = AmaterasuConstants.DEFAULT_SURROUND_TAG;
    /** Markdown: the situation, objectives, how to get in and the rules. */
    @Column(columnDefinition = "TEXT")
    private String scenario;
    /** The lab definition players work in; null for a plain CTF. */
    @ManyToOne
    @JoinColumn(name = "environment_id")
    @JsonIgnore
    private Lab environment;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EnvironmentMode environmentMode = EnvironmentMode.NONE;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TeamMode teamMode = TeamMode.TEAMS_OPTIONAL;
    /** Most players on one team; null for no limit. */
    @Builder.Default
    private Integer maxTeamSize = 4;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TeamCredit teamCredit = TeamCredit.SEE_TEAMMATES;

    @JsonProperty("environmentId")
    public String getEnvironmentId() {
        return environment != null ? environment.getId() : null;
    }

    @JsonIgnore
    public TeamMode effectiveTeamMode() {
        return teamMode == null ? TeamMode.TEAMS_OPTIONAL : teamMode;
    }

    @JsonIgnore
    public TeamCredit effectiveTeamCredit() {
        return teamCredit == null ? TeamCredit.SEE_TEAMMATES : teamCredit;
    }

    /** NONE when there is nothing to run, whatever the stored mode says. */
    @JsonIgnore
    public EnvironmentMode effectiveMode() {
        return environment == null || environmentMode == null ? EnvironmentMode.NONE : environmentMode;
    }
}
