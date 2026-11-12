package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/** The caller of the current request, taken from the verified access token. */
@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserService userService;
    private final RoomRepository roomRepository;
    private final TeamResolver teamResolver;

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

    /** Admins run every room; a facilitator only runs the rooms they were assigned to. */
    public boolean canRun(Room room) {
        if (isAdmin()) {
            return true;
        }
        return hasRole(Role.FACILITATOR) && room.getFacilitators() != null && room.getFacilitators().contains(id());
    }

    /** Drafts are seen only by authors and the people who run the lab. */
    public boolean canSee(Room room) {
        return room.isLive() || isAdmin() || hasRole(Role.CREATOR) || canRun(room);
    }

    public Room requireCanSee(Room room) {
        if (!canSee(room)) {
            throw new ResourceNotFoundException("Lab " + room.getId() + " not found");
        }
        return room;
    }

    public void requireCanRun(Room room) {
        if (!canRun(room)) {
            throw new AccessDeniedException("You do not facilitate " + room.getName());
        }
    }

    /**
     * Who may see a copy and its login: those who run it, everyone who can see the lab for a shared
     * copy, and for a team or solo copy whoever a per-team lab running it resolves to that copy.
     */
    public void requireTrackerAccess(LabTracker labTracker) {
        if (!canAccess(labTracker)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    public boolean canAccess(LabTracker labTracker) {
        if (canControl(labTracker)) {
            return true;
        }
        if (labTracker.isShared()) {
            return roomRepository.findById(labTracker.getRoomId()).map(this::canSee).orElse(false);
        }
        return teamResolver.worksIn(user(), labTracker);
    }

    /** Who may start, pause, resume or stop a copy: admins, and the facilitators of a lab that runs it. */
    public void requireTrackerControl(LabTracker labTracker) {
        if (!canControl(labTracker)) {
            throw new AccessDeniedException("Only admins and the lab's facilitators start and stop its environment");
        }
    }

    public boolean canControl(LabTracker labTracker) {
        if (isAdmin()) {
            return true;
        }
        if (!hasRole(Role.FACILITATOR)) {
            return false;
        }
        return roomsRunning(labTracker).stream().anyMatch(this::canRun);
    }

    /** The lab a shared copy belongs to, or every per-team lab using the copy's environment. */
    private List<Room> roomsRunning(LabTracker labTracker) {
        if (labTracker.isShared()) {
            return roomRepository.findById(labTracker.getRoomId()).map(List::of).orElse(List.of());
        }
        String environmentId = labTracker.getLabStarted() != null ? labTracker.getLabStarted().getId() : null;
        return roomRepository.findAll().stream()
                .filter(room -> room.effectiveMode() == EnvironmentMode.PER_TEAM && environmentId != null
                        && environmentId.equals(room.getEnvironmentId()))
                .toList();
    }
}
