package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AnsweredCTFEntityResponse extends StoredObject {
    private CTFEntityResponse ctfEntity;
    private Boolean correct;
    private Integer attempts;
    private List<CTFEntityAnswerRequest> answers;
    private List<LocalDateTime> attemptTimes;
    private LocalDateTime solvedAt;
    private LocalDateTime lastAttemptAt;
    private Integer score;
    private List<Hint> hintsUsed;
    private Long solveTimeSeconds;
    private JoinRoomResponse joinRoomResponse;
}
