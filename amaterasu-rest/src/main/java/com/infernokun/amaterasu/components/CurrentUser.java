package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
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
    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;

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

    public void requireCanRun(Room room) {
        if (!canRun(room)) {
            throw new AccessDeniedException("You do not facilitate " + room.getName());
        }
    }

    /**
     * Who may see a copy and its login: the owning team, or for a shared copy everyone who joined its
     * lab and the people who run it. Admins see every copy.
     */
    public void requireTrackerAccess(LabTracker labTracker) {
        if (isAdmin()) {
            return;
        }
        if (labTracker.isShared()) {
            Room room = roomRepository.findById(labTracker.getRoomId()).orElse(null);
            if (room != null && (canRun(room) || joined(room))) {
                return;
            }
            throw new AccessDeniedException("Access denied");
        }
        requireTeamOwns(labTracker);
    }

    /** Who may start, pause or stop a copy: the owning team, or for a shared copy whoever runs its lab. */
    public void requireTrackerControl(LabTracker labTracker) {
        if (isAdmin()) {
            return;
        }
        if (labTracker.isShared()) {
            Room room = roomRepository.findById(labTracker.getRoomId()).orElse(null);
            if (room != null && canRun(room)) {
                return;
            }
            throw new AccessDeniedException("Only the lab's facilitators start and stop its shared environment");
        }
        requireTeamOwns(labTracker);
    }

    /** Whether the caller joined the room and is still in it. */
    public boolean joined(Room room) {
        return roomUserRepository.findByUserAndRoom(user(), room)
                .map(roomUser -> roomUser.getRoomUserStatus() == RoomUserStatus.JOINED).orElse(false);
    }

    private void requireTeamOwns(LabTracker labTracker) {
        User user = user();
        if (user.getTeam() == null || labTracker.getLabOwner() == null
                || !user.getTeam().getId().equals(labTracker.getLabOwner().getId())) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
