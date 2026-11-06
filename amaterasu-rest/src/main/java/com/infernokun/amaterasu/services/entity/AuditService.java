package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.repositories.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records who did what. Recording runs in its own transaction and never throws, so an audit
 * problem can't fail or roll back the action being recorded.
 */
@Service
public class AuditService {
    public static final String SYSTEM = "system";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_TEXT = 255;
    private static final int MAX_DETAIL = 1000;

    private final AuditEventRepository repository;
    private final CurrentUser currentUser;
    private final TransactionTemplate ownTransaction;
    private final ApplicationEventPublisher events;

    public AuditService(AuditEventRepository repository, CurrentUser currentUser,
                        PlatformTransactionManager transactionManager, ApplicationEventPublisher events) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.events = events;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Filters for {@link #search}; null fields match everything. */
    public record Filter(String targetType, String targetId, String teamId, List<String> actions,
                         Instant from, Instant to) {
    }

    /** Records an action by the caller of the current request, or by "system" outside a request. */
    public void record(String action, String targetType, String targetId, String targetLabel, String teamId,
                       String detail) {
        String actorId = null;
        String actorUsername = SYSTEM;
        try {
            if (currentUser.isAuthenticated()) {
                actorId = currentUser.id();
                actorUsername = currentUser.username();
            }
        } catch (RuntimeException e) {
            LOGGER.debug("No actor for audit event {}", action, e);
        }
        recordAs(actorId, actorUsername, action, targetType, targetId, targetLabel, teamId, detail);
    }

    /** Records an action by a known actor, for requests that are not signed in yet, such as sign in. */
    public void recordAs(String actorId, String actorUsername, String action, String targetType, String targetId,
                         String targetLabel, String teamId, String detail) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .at(Instant.now())
                    .actorId(actorId)
                    .actorUsername(clip(actorUsername == null ? SYSTEM : actorUsername, MAX_TEXT))
                    .action(clip(action, 64))
                    .targetType(clip(targetType, 32))
                    .targetId(clip(targetId, MAX_TEXT))
                    .targetLabel(clip(targetLabel, MAX_TEXT))
                    .teamId(teamId)
                    .detail(clip(detail, MAX_DETAIL))
                    .build();
            ownTransaction.executeWithoutResult(status -> repository.save(event));
            events.publishEvent(new AuditRecordedEvent(event));
        } catch (RuntimeException e) {
            LOGGER.warn("Could not record audit event {} on {} {}", action, targetType, targetId, e);
        }
    }

    public Page<AuditEvent> search(Filter filter, Pageable pageable) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.targetType() != null) {
                predicates.add(cb.equal(root.get("targetType"), filter.targetType()));
            }
            if (filter.targetId() != null) {
                predicates.add(cb.equal(root.get("targetId"), filter.targetId()));
            }
            if (filter.teamId() != null) {
                predicates.add(cb.equal(root.get("teamId"), filter.teamId()));
            }
            if (filter.actions() != null && !filter.actions().isEmpty()) {
                predicates.add(root.get("action").in(filter.actions()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("at"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("at"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    /** "A,B" to [A, B]; blank to null. */
    public static List<String> splitActions(String actions) {
        if (actions == null || actions.isBlank()) {
            return null;
        }
        return Arrays.stream(actions.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
