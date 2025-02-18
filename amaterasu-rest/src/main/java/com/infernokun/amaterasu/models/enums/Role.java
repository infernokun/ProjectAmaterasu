package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum Role {
    DEVELOPER("Developer"),
    ADMIN("Admin"),
    TEAM_ADMIN("Team Admin"),
    MEMBER("Member");

    private final String value;

    Role(final String value) {
        this.value = value;
    }

    @JsonValue
    final String value() {
        return this.value;
    }
}
