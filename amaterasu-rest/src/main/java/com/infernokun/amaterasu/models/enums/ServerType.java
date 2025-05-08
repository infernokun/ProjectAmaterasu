package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum ServerType {
    DOCKER_HOST("DOCKER_HOST"),
    PROXMOX("PROXMOX"),
    UNKNOWN("UNKNOWN");

    private final String value;

    @JsonValue
    final String value() {
        return this.value;
    }
}
