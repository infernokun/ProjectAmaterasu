package com.infernokun.amaterasu.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PointsHistoryEntry {
    private LocalDateTime timestamp;
    private Integer totalPoints;
    private Integer pointsChange;
    private String reason;
}