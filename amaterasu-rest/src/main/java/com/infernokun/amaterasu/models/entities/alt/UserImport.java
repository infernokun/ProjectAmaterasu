package com.infernokun.amaterasu.models.entities.alt;

import java.util.List;

/** Accounts imported from a CSV file the admin's browser parsed. */
public final class UserImport {
    private UserImport() {
    }

    /** One row: team is a team name, blank for no team. Row is the line number in the file. */
    public record Row(Integer row, String username, String password, String role, String team) {
    }

    public record Request(List<Row> users) {
    }

    public record Created(Integer row, String id, String username, String role, String team) {
    }

    public record Skipped(Integer row, String username, String reason) {
    }

    public record Result(List<Created> created, List<Skipped> skipped) {
    }

    /** Moves several accounts at once; a null team takes them off their team. */
    public record BulkMove(List<String> userIds, String teamId) {
    }
}
