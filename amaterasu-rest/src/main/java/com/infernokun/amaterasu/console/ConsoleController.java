package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Opens a browser console on one VM of a Proxmox lab. The caller's team must own the lab (admins may
 * open any). The browser then connects to {@code /socket-handler/console/{sessionId}}, which relays to
 * the node, so it never talks to Proxmox itself.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-tracker")
public class ConsoleController extends BaseController {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final LabTrackerService labTrackerService;
    private final ProxmoxService proxmoxService;
    private final ConsoleSessions consoleSessions;
    private final CurrentUser currentUser;

    /** {@code password} is the VNC password the node expects, valid for this one connection. */
    public record ConsoleSession(String sessionId, String path, String password, int vmid, String vmName) {
    }

    @PostMapping("/{labTrackerId}/vms/{vmid}/console")
    public ResponseEntity<ApiResponse<ConsoleSession>> open(@PathVariable String labTrackerId, @PathVariable int vmid) {
        LabTracker tracker = labTrackerService.findLabTrackerById(labTrackerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
        currentUser.requireTrackerAccess(tracker);

        ProxmoxVM vm = tracker.getVms() == null ? null : tracker.getVms().stream()
                .filter(candidate -> candidate.getVmid() == vmid).findFirst().orElse(null);
        if (vm == null) {
            throw new ResourceNotFoundException("VM " + vmid + " is not part of this lab");
        }
        RemoteServer server = tracker.getRemoteServer();
        if ((tracker.getLabStarted() != null && tracker.getLabStarted().getLabType() != LabType.VIRTUAL_MACHINE)
                || server == null || server.getServerType() != ServerType.PROXMOX) {
            return unavailable(HttpStatus.CONFLICT, "Consoles are only available for VMs on a Proxmox node.");
        }
        if (vm.getStatus() != null && !"running".equals(vm.getStatus())) {
            return unavailable(HttpStatus.CONFLICT, vm.getName() + " is " + vm.getStatus() + ". Start or resume the lab first.");
        }

        ProxmoxVncTicket ticket;
        try {
            ticket = proxmoxService.createVncProxy(server, vmid);
        } catch (HttpStatusCodeException e) {
            LOGGER.warn("Proxmox refused a console for VM {} on {}: {} {}", vmid, server.getName(), e.getStatusCode(),
                    e.getResponseBodyAsString());
            return unavailable(HttpStatus.BAD_GATEWAY, server.getName() + " refused the console: " + reason(e));
        } catch (IllegalStateException e) {
            return unavailable(HttpStatus.BAD_GATEWAY, e.getMessage());
        }

        String sessionId = consoleSessions.issue(currentUser.id(), server.getId(), vmid, ticket);
        return createSuccessResponse(new ConsoleSession(sessionId, "/socket-handler/console/" + sessionId,
                ticket.ticket(), vmid, vm.getName()), "Console ready.");
    }

    private static ResponseEntity<ApiResponse<ConsoleSession>> unavailable(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<ConsoleSession>builder()
                .code(status.value()).message(message).build());
    }

    // Proxmox puts the reason in {"message": "..."}, usually with a trailing newline
    private static String reason(HttpStatusCodeException e) {
        try {
            com.fasterxml.jackson.databind.JsonNode message = JSON.readTree(e.getResponseBodyAsString()).get("message");
            if (message != null && message.isTextual() && !message.asText().isBlank()) {
                return message.asText().trim();
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // not JSON
        }
        return e.getStatusText() != null && !e.getStatusText().isBlank() ? e.getStatusText() : "HTTP " + e.getStatusCode().value();
    }
}
