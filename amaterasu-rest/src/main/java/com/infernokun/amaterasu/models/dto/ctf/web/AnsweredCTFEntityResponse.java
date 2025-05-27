package com.infernokun.amaterasu.models.dto.ctf.web;

import com.infernokun.amaterasu.models.dto.ctf.FlagAnswer;
import com.infernokun.amaterasu.models.entities.StoredObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AnsweredCTFEntityResponse extends StoredObject {
    private CTFEntityResponseDTO ctfEntity;
    private Boolean correct;
    private Integer attempts;
    private List<FlagAnswer> answers;
    private List<LocalDateTime> attemptTimes;
    private LocalDateTime solvedAt;
    private LocalDateTime lastAttemptAt;
    private Integer score;
    private Integer hintsUsed;
    private Long solveTimeSeconds;
}
