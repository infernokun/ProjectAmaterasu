package com.infernokun.amaterasu.models.dto.ctf;

import lombok.Data;

import java.util.List;

@Data
public class CTFEntityCreateDTO {
    private String question;
    private Integer maxAttempts;
    private String roomId;  // Just the ID, not the full object
    private String description;
    private List<String> hints;
    private List<FlagCreateDTO> flags;  // Use DTO
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