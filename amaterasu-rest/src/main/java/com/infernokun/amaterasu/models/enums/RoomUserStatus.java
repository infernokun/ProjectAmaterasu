package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoomUserStatus {
    NONE("NONE"),
    JOINED("JOINED"),
    LEFT("LEFT"),
    KICKED("KICKED"),
    BANNED("BANNED");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}
