package com.infernokun.amaterasu.services.notification;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Notification;
import com.infernokun.amaterasu.repositories.NotificationRepository;
import com.infernokun.amaterasu.websocket.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Stores notifications and reads them back for their recipient only. */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MAX_TITLE = 255;
    private static final int MAX_BODY = 1000;
    private static final Duration KEEP = Duration.ofDays(90);

    private final NotificationRepository repository;
    private final ApplicationEventPublisher events;

    /** One notification per recipient. Recipients that got the same type and link within {@code quiet} are skipped. */
    @Transactional
    public List<Notification> send(Collection<String> recipientIds, String type, String title, String body, String link,
                                   Duration quiet) {
        Instant now = Instant.now();
        List<Notification> sent = new ArrayList<>();
        for (String recipientId : new LinkedHashSet<>(recipientIds)) {
            if (recipientId == null) {
                continue;
            }
            if (quiet != null && link != null
                    && repository.existsByRecipientIdAndTypeAndLinkAndCreatedAtAfter(recipientId, type, link, now.minus(quiet))) {
                continue;
            }
            Notification saved = repository.save(Notification.builder()
                    .recipientId(recipientId)
                    .type(type)
                    .title(clip(title, MAX_TITLE))
                    .body(clip(body, MAX_BODY))
                    .link(link)
                    .createdAt(now)
                    .build());
            events.publishEvent(new NotificationCreatedEvent(saved));
            sent.add(saved);
        }
        return sent;
    }

    @Transactional
    public List<Notification> send(Collection<String> recipientIds, String type, String title, String body, String link) {
        return send(recipientIds, type, title, body, link, null);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(String userId, Pageable pageable) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return repository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(String notificationId, String userId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!Objects.equals(notification.getRecipientId(), userId)) {
            throw new AccessDeniedException("Access denied");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = repository.save(notification);
        }
        return notification;
    }

    @Transactional
    public int markAllRead(String userId) {
        return repository.markAllRead(userId, Instant.now());
    }

    @Scheduled(cron = "${NOTIFICATION_CLEANUP_CRON:0 17 3 * * *}")
    @Transactional
    public void deleteOld() {
        repository.deleteOlderThan(Instant.now().minus(KEEP));
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
