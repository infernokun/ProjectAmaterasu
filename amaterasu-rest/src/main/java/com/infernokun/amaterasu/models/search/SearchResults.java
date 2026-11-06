package com.infernokun.amaterasu.models.search;

import java.util.List;

/** What the top bar search found, limited to what the caller may open. Never carries flags. */
public record SearchResults(String query, List<Hit> hits) {

    public enum Type { TEAM_LAB, LAB, ROOM, CHALLENGE, USER, TEAM, SERVER }

    /**
     * One match. {@code roomId} is set for challenges, {@code status} for team labs, servers and
     * users (their role).
     */
    public record Hit(Type type, String id, String title, String subtitle, String roomId, String status) {
    }

    public static SearchResults empty(String query) {
        return new SearchResults(query, List.of());
    }
}
