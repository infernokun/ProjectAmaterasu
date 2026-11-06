package com.infernokun.amaterasu.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PointsHistoryEntry {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime timestamp;
    private Integer totalPoints;
    private Integer pointsChange;
    private String reason;
    /** Username of the facilitator or admin who awarded the change; empty for solves and hints. */
    private String awardedBy;

    public PointsHistoryEntry(LocalDateTime timestamp, Integer totalPoints, Integer pointsChange, String reason) {
        this(timestamp, totalPoints, pointsChange, reason, null);
    }
}