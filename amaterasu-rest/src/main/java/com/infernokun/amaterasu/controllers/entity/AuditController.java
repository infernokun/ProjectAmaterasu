package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.services.entity.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** The audit log, newest first. Admins see everything; everyone else sees their own team's events. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/audit")
public class AuditController extends BaseController {
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditEvent>>> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "at"));

        String scopedTeam = blankToNull(teamId);
        if (!currentUser.isAdmin()) {
            Team team = currentUser.user().getTeam();
            if (team == null || (scopedTeam != null && !scopedTeam.equals(team.getId()))) {
                return createSuccessResponse(new PageImpl<>(List.of(), pageable, 0), "No events for your team.");
            }
            scopedTeam = team.getId();
        }

        AuditService.Filter filter = new AuditService.Filter(blankToNull(targetType), blankToNull(targetId),
                scopedTeam, AuditService.splitActions(action), from, to);
        return createSuccessResponse(auditService.search(filter, pageable), "Audit events retrieved.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
