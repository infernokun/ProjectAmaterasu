package com.infernokun.amaterasu.models.entities.ctf;

import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Points given to, or taken from, a team as a whole in one lab, on top of its members' points. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "team_points_award", indexes = @Index(name = "idx_team_award_room", columnList = "room_id"))
public class TeamPointsAward extends StoredObject {
    @Column(name = "room_id", nullable = false)
    private String roomId;
    @Column(name = "team_id", nullable = false)
    private String teamId;
    private String teamName;
    private Integer points;
    @Column(length = 500)
    private String reason;
    private String awardedBy;
    private LocalDateTime awardedAt;
}
