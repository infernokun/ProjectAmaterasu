package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum Role {
    CREATOR("Creator"),
    DEVELOPER("DEVELOPER"),
    FACILITATOR("Facilitator"),
    ADMIN("ADMIN"),
    TEAM_ADMIN("TEAM_ADMIN"),
    MEMBER("MEMBER");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}
