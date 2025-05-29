package com.infernokun.amaterasu.models.dto.ctf;

import lombok.Data;

@Data
public class FlagRequest {
    private String flag;
    private Boolean surroundWithTag;
    private Boolean caseSensitive;
    private Double weight;
}