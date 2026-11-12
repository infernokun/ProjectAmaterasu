package com.infernokun.amaterasu.models.entities.ctf.dto.manage;

import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;

import java.util.List;

/**
 * A lab's team setup for the people who run it: which teams count, the lab's own teams (kept even
 * while another mode is picked) and the site teams for reference.
 */
public record LabTeamsView(
        String roomId,
        String roomName,
        TeamMode teamMode,
        TeamCredit teamCredit,
        EnvironmentMode environmentMode,
        List<TeamView> labTeams,
        List<TeamView> siteTeams) {

    /** {@code copyStatus} is the team's copy of a per-team environment, null when the lab has none. */
    public record TeamView(String id, String name, String description, List<Member> members,
                           EnvironmentStatus copyStatus) {
    }

    public record Member(String userId, String username, String displayName) {
    }

    /** Someone who can be put on a lab team, with the teams they are on now. */
    public record Candidate(String userId, String username, String displayName, String role, String siteTeamName,
                            String labTeamId, String labTeamName, boolean played) {
    }
}
