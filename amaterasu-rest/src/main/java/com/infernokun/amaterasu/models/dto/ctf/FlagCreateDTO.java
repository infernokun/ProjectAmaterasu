package com.infernokun.amaterasu.models.dto.ctf;

import lombok.Data;

@Data
public class FlagCreateDTO {
    private String flag;
    private Boolean surroundWithTag;
    private Boolean caseSensitive;
    private Double weight;
}