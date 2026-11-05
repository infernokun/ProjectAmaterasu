package com.infernokun.amaterasu.models.entities.ctf.dto;

import lombok.Data;

@Data
public class HintResponse {
    private String id;
    private Integer cost;
    private Integer orderIndex;
    private Integer unlockAfterAttempts;
    private Boolean isUnlocked;
    private Integer pointsDeducted;
}
