package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum LabType {
    UNKNOWN("UNKNOWN"),
    DOCKER_CONTAINER("DOCKER_CONTAINER"),
    DOCKER_COMPOSE("DOCKER_COMPOSE"),
    VIRTUAL_MACHINE("VIRTUAL_MACHINE"),
    KUBERNETES("KUBERNETES");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}
