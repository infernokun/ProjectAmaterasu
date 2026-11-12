package com.infernokun.amaterasu.models.entities.ctf;

import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import jakarta.persistence.*;
import lombok.*;

/** A user on one of a lab's own teams. A user is on at most one team per lab. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab_team_member", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
public class LabTeamMember extends StoredObject {
    @Column(name = "room_id", nullable = false)
    private String roomId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
