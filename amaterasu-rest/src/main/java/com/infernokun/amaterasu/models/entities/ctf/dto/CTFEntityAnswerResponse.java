package com.infernokun.amaterasu.models.entities.ctf.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CTFEntityAnswerResponse {
    private Boolean correct;
    private Integer attempts;
    private List<CTFEntityAnswerRequest> answers;
    private List<LocalDateTime> attemptTimes;
    private LocalDateTime solvedAt;
    private LocalDateTime lastAttemptAt;
    private Integer score;
    private List<CTFEntityHintUsageResponse> hintUsages;
    private Long solveTimeSeconds;
    private JoinRoomResponse joinRoomResponse;
}
