package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum LabStatus {
    NONE("NONE"),
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    HIDDEN("HIDDEN"),
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    DELETED("DELETED"),
    STOPPED("STOPPED"),
    RETIRED("RETIRED"),
    FAILED("FAILED"),
    ACTIVE_FAILED("ACTIVE_FAILED"),
    FAILED_ACTIVE("FAILED_ACTIVE");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}
