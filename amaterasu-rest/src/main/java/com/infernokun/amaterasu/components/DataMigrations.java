package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fills columns added after data already existed. Hibernate adds the columns empty; each step
 * here only touches rows where they are still empty, so running it again changes nothing.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DataMigrations implements ApplicationRunner {
    private final EntityManager entityManager;
    private final CTFEntityRepository ctfEntityRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            publishExistingLabs();
            orderExistingChallenges();
        });
    }

    /** Labs from before drafts existed were already visible to teams, so they stay published. */
    void publishExistingLabs() {
        int updated = entityManager.createQuery("UPDATE Lab l SET l.published = true WHERE l.published IS NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Marked {} existing labs as published", updated);
        }
    }

    /**
     * Challenges without a position get one after the room's positioned challenges, in the order
     * rooms used to show them: by category, then points.
     */
    void orderExistingChallenges() {
        List<CTFEntity> unordered = ctfEntityRepository.findAll().stream()
                .filter(challenge -> challenge.getSortOrder() == null && challenge.getRoom() != null)
                .toList();
        if (unordered.isEmpty()) {
            return;
        }
        Map<String, List<CTFEntity>> byRoom = unordered.stream()
                .collect(Collectors.groupingBy(challenge -> challenge.getRoom().getId()));
        byRoom.forEach((roomId, challenges) -> {
            int next = ctfEntityRepository.findByRoomId(roomId).stream()
                    .map(CTFEntity::getSortOrder).filter(Objects::nonNull)
                    .max(Integer::compare).map(max -> max + 1).orElse(0);
            List<CTFEntity> sorted = challenges.stream().sorted(Comparator
                    .comparing((CTFEntity challenge) -> challenge.getCategory() == null ? "" : challenge.getCategory())
                    .thenComparing(challenge -> challenge.getPoints() == null ? 0 : challenge.getPoints())
                    .thenComparing(challenge -> challenge.getQuestion() == null ? "" : challenge.getQuestion()))
                    .toList();
            for (CTFEntity challenge : sorted) {
                challenge.setSortOrder(next++);
            }
            ctfEntityRepository.saveAll(sorted);
        });
        log.info("Gave {} existing challenges a position in their room", unordered.size());
    }
}
