package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum CTFType {
    TEXT("TEXT"),
    CODE("CODE"),
    BIG_TEXT("BIG_TEXT"),
    RADIO("RADIO"),
    CHECKBOX("CHECKBOX"),
    DATE("DATE"),
    TIME("TIME"),
    RANGE("RANGE");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}