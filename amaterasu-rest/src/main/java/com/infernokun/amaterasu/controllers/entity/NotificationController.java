package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Notification;
import com.infernokun.amaterasu.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own notifications, newest first. Nobody can read or mark anyone else's. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController extends BaseController {
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public record UnreadCount(long count) {
    }

    public record MarkedRead(int count) {
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Notification>>> list(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return createSuccessResponse(notificationService.list(currentUser.id(), pageable), "Notifications retrieved.");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> unreadCount() {
        return createSuccessResponse(new UnreadCount(notificationService.unreadCount(currentUser.id())), "Unread count.");
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable String id) {
        return createSuccessResponse(notificationService.markRead(id, currentUser.id()), "Marked read.");
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<MarkedRead>> markAllRead() {
        return createSuccessResponse(new MarkedRead(notificationService.markAllRead(currentUser.id())), "All marked read.");
    }
}
