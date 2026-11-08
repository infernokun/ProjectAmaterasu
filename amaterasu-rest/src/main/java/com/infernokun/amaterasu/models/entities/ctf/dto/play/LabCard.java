package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamMode;

/**
 * One lab in the Labs list: a line from its scenario, its environment as the viewer sees it, and
 * how far the viewer got. {@code environment} is null for a lab with nothing to run.
 */
public record LabCard(
        String id,
        String name,
        String summary,
        EnvironmentMode environmentMode,
        EnvironmentView environment,
        int questionCount,
        int playerCount,
        boolean joined,
        boolean left,
        int points,
        int solved,
        boolean canRun,
        TeamMode teamMode,
        /* The viewer's team in this lab, if they are on one. */
        String teamId,
        String teamName) {
}
