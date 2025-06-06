package com.infernokun.amaterasu.models.entities.ctf.dto;

import lombok.Data;

@Data
public class HintResponse {
    private String id;
    private Integer cost;
    private Boolean isUnlocked;
    private Integer pointsDeducted;
}
