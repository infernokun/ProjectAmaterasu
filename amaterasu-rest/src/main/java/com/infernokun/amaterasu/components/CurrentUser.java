package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The caller of the current request, taken from the verified access token. */
@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserService userService;

    public String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return token.getName();
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token && token.isAuthenticated();
    }

    /** The username claim of the access token. */
    public String username() {
        id();
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Object username = token.getToken().getClaims().get("username");
        return username != null ? username.toString() : token.getName();
    }

    public User user() {
        return userService.findUserById(id());
    }

    public boolean hasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public void requireSelfOrAdmin(String userId) {
        if (!isAdmin() && !id().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /** Lab trackers belong to a team; only that team's members and admins may act on one. */
    public void requireTrackerAccess(LabTracker labTracker) {
        if (isAdmin()) {
            return;
        }
        User user = user();
        if (user.getTeam() == null || labTracker.getLabOwner() == null
                || !user.getTeam().getId().equals(labTracker.getLabOwner().getId())) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
