package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.entities.ctf.Hint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CTFEntityHintUsageResponse {
    private Hint hint;
    private LocalDateTime usedAt;
    private Integer pointsDeducted;
    private Integer usageOrder;
}
