package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.PointsHistoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUserProfileResponse {
    private String username;
    private Integer points;
    private List<PointsHistoryEntry> pointsHistory;
    private Integer fails;
    private Integer correct;
    private Map<String, Integer> correctByCategory;
    private String place;
}
