package com.infernokun.amaterasu.models.dto.ctf;

import lombok.Data;

@Data
public class HintResponse {
    private Integer orderIndex;
    private Integer cost;
    private Boolean isUnlocked;
    private Integer pointsDeducted;
}
