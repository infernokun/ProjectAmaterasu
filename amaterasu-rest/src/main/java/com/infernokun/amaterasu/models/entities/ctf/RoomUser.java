package com.infernokun.amaterasu.models.entities.ctf;

import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUser extends StoredObject {
    @ManyToOne
    private Room room;
    @ManyToOne
    private User user;
    @Builder.Default
    private Integer points = 0;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoomUserStatus roomUserStatus = RoomUserStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    @Builder.Default
    private List<PointsHistoryEntry> pointsHistory = new ArrayList<>();

    public void updatePoints(Integer newPoints, String reason) {
        PointsHistoryEntry entry = new PointsHistoryEntry(
                LocalDateTime.now(),
                newPoints,
                newPoints - this.points,
                reason
        );

        this.pointsHistory.add(entry);
        this.points = newPoints;
    }
}
