package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.entities.ctf.Hint;
import lombok.Data;

import java.util.List;

@Data
public class CTFEntityRequest {
    private String question;
    private Integer maxAttempts;
    private String roomId;
    private String description;
    private List<Hint> hints;
    private List<FlagRequest> flags;
    private String category;
    private String difficultyLevel;
    private Integer points;
    private String author;
    private List<String> tags;
    private Boolean visible;
    private String expirationDate;
    private List<String> attachments;
    private String solutionExplanation;
    private List<String> relatedChallengeIds;
}