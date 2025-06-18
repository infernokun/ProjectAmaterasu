package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.PointsHistoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUserResponse {
    private String username;
    private Integer points;
    private List<PointsHistoryEntry> pointsHistory;
}
