package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.enums.EnvironmentMode;

/**
 * One lab in the Labs list: a line from its scenario, its environment as the viewer sees it, and
 * how far the viewer got. {@code environment} is null for a lab with nothing to run; its copies are
 * left out here. {@code teamId} and {@code teamName} are the viewer's team, if an admin put them on one.
 */
public record LabCard(
        String id,
        String name,
        String summary,
        EnvironmentMode environmentMode,
        EnvironmentView environment,
        int questionCount,
        int playerCount,
        int points,
        int solved,
        boolean canRun,
        String teamId,
        String teamName) {
}
