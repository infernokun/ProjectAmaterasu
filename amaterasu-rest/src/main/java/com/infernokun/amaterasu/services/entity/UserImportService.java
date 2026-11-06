package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.alt.UserImport;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates accounts from imported rows. Each row is checked and created on its own, so one bad row
 * is reported and skipped while the rest are created.
 */
@Service
@RequiredArgsConstructor
public class UserImportService {
    public static final int MAX_ROWS = 1000;
    static final Set<Role> IMPORTABLE = Set.of(Role.MEMBER, Role.FACILITATOR, Role.CREATOR, Role.ADMIN);

    private final AuthenticationService authenticationService;
    private final TeamService teamService;
    private final UserService userService;
    private final AuditService auditService;

    public UserImport.Result importUsers(List<UserImport.Row> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("The file has no accounts to import");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Import at most " + MAX_ROWS + " accounts at a time");
        }
        Map<String, Team> teams = teamService.findAllTeams().stream()
                .filter(team -> team.getName() != null)
                .collect(Collectors.toMap(team -> team.getName().trim().toLowerCase(Locale.ROOT), Function.identity(),
                        (a, b) -> a));
        Set<String> seen = new HashSet<>();
        List<UserImport.Created> created = new ArrayList<>();
        List<UserImport.Skipped> skipped = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            UserImport.Row row = rows.get(i);
            Integer line = row == null || row.row() == null ? i + 1 : row.row();
            String username = row == null || row.username() == null ? "" : row.username().trim();
            try {
                if (row == null) {
                    throw new IllegalArgumentException("Empty row");
                }
                if (!seen.add(username.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Listed more than once in the file");
                }
                Role role = role(row.role());
                String teamName = row.team() == null ? "" : row.team().trim();
                Team team = null;
                if (!teamName.isEmpty()) {
                    team = teams.get(teamName.toLowerCase(Locale.ROOT));
                    if (team == null) {
                        throw new IllegalArgumentException("No team named " + teamName);
                    }
                }
                if (userService.existsByUsernameIgnoreCase(username)) {
                    throw new IllegalArgumentException("An account named " + username + " already exists");
                }
                User user = authenticationService.createUser(username, row.password(), role, team);
                created.add(new UserImport.Created(line, user.getId(), user.getUsername(), user.getRole().name(),
                        team != null ? team.getName() : null));
            } catch (IllegalArgumentException e) {
                skipped.add(new UserImport.Skipped(line, username, e.getMessage()));
            }
        }
        auditService.record("USERS_IMPORTED", "USER", null, created.size() + " accounts", null,
                created.size() + " created, " + skipped.size() + " skipped");
        return new UserImport.Result(created, skipped);
    }

    static Role role(String value) {
        if (value == null || value.isBlank()) {
            return Role.MEMBER;
        }
        try {
            Role role = Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (IMPORTABLE.contains(role)) {
                return role;
            }
        } catch (IllegalArgumentException ignored) {
            // Reported below with the accepted values
        }
        throw new IllegalArgumentException("Role " + value.trim() + " is not one of Member, Facilitator, Creator or Admin");
    }
}
