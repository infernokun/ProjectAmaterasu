package com.infernokun.amaterasu.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum DifficultyLevel {
    BEGINNER(1), EASY(2), MEDIUM(3), HARD(4), EXPERT(5), IMPOSSIBLE(6);

    private final int level;

    final int value() {
        return this.level;
    }
}