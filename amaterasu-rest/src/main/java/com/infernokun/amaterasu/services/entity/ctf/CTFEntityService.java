package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityHintUsageRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.HintRepository;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CTFEntityService {
    private final CTFEntityRepository ctfEntityRepository;
    private final FlagRepository flagRepository;
    private final HintRepository hintRepository;
    private final CTFAnswerRepository ctfAnswerRepository;
    private final CTFEntityHintUsageRepository hintUsageRepository;

    public CTFEntity findCTFEntityById(String id) {
        log.info("Fetching CTF entity with id: {}", id);

        return ctfEntityRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("CTF entity not found with id: {}", id);
                    return new ResourceNotFoundException("CTF entity not found with id: " + id);
                });
    }

    public CTFEntity findCTFEntityByIdWithFlags(String id) {
        log.info("Fetching CTF entity with flags and id: {}", id);

        return ctfEntityRepository.findByIdWithFlags(id)
                .orElseThrow(() -> new ResourceNotFoundException("CTF entity not found with id: " + id));
    }

    public List<CTFEntity> findCTFEntitiesByRoomId(String roomId) {
        log.info("Fetching CTF entities for room: {}", roomId);

        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room ID cannot be null or empty");
        }

        return ctfEntityRepository.findByRoomId(roomId);
    }

    @Transactional
    public CTFEntity createCTFEntity(CTFEntity ctfEntity) {
        log.info("Creating CTF entity: {}", ctfEntity.getQuestion());
        log.info("Flags before processing: {}", ctfEntity.getFlags() != null ? ctfEntity.getFlags().size() : "null");

        validateCTFEntity(ctfEntity);

        if (!CollectionUtils.isEmpty(ctfEntity.getHints())) {
            log.info("Setting bidirectional relationships for {} hints", ctfEntity.getFlags().size());
            ctfEntity.getHints().forEach(hint -> {
                log.info("Setting ctfEntity for hint: '{}'", hint.getHint());
                hint.setCtfEntity(ctfEntity);
            });
        }

        // Set bidirectional relationship properly
        if (!CollectionUtils.isEmpty(ctfEntity.getFlags())) {
            log.info("Setting bidirectional relationships for {} flags", ctfEntity.getFlags().size());
            ctfEntity.getFlags().forEach(flag -> {
                log.info("Setting ctfEntity for flag: '{}'", flag.getFlag());
                flag.setCtfEntity(ctfEntity);
            });
        }

        CTFEntity savedEntity = ctfEntityRepository.save(ctfEntity);

        // Check what was actually saved
        log.info("Entity saved with ID: {}", savedEntity.getId());
        if (savedEntity.getFlags() != null) {
            log.info("Flags after save: {}", savedEntity.getFlags().size());
            savedEntity.getFlags().forEach(flag ->
                    log.info("Saved flag: '{}', ID: {}", flag.getFlag(), flag.getId())
            );
        }

        return savedEntity;
    }

    @Transactional
    public List<CTFEntity> createCTFEntities(List<CTFEntity> ctfEntities) {
        log.info("Creating {} CTF entities", ctfEntities.size());

        if (CollectionUtils.isEmpty(ctfEntities)) {
            throw new IllegalArgumentException("CTF entities list cannot be empty");
        }
        for (int i = 0; i < ctfEntities.size(); i++) {
            CTFEntity entity = ctfEntities.get(i);
            if (entity == null || entity.getQuestion() == null || entity.getQuestion().isBlank()) {
                throw new IllegalArgumentException("Challenge " + (i + 1) + " has no title");
            }
            entity.setId(null);
            entity.setVersioning(null);
            if (entity.getFlags() != null) {
                entity.getFlags().forEach(flag -> {
                    flag.setId(null);
                    flag.setVersioning(null);
                    flag.setCtfEntity(entity);
                });
            }
            if (entity.getHints() != null) {
                entity.getHints().forEach(hint -> {
                    hint.setId(null);
                    hint.setVersioning(null);
                    hint.setCtfEntity(entity);
                });
            }
        }

        List<CTFEntity> savedEntities = ctfEntityRepository.saveAll(ctfEntities);

        log.info("Successfully created {} CTF entities", savedEntities.size());
        return savedEntities;
    }

    @Transactional
    public CTFEntity updateCTFEntity(String id, CTFEntity updatedEntity) {
        log.info("Updating CTF entity with id: {}", id);

        CTFEntity existingEntity = findCTFEntityById(id);

        // Update fields
        updateEntityFields(existingEntity, updatedEntity);

        CTFEntity savedEntity = ctfEntityRepository.save(existingEntity);

        log.info("Successfully updated CTF entity with id: {}", id);
        return savedEntity;
    }

    @Transactional(readOnly = true)
    public List<CTFEntity> findAllCTFEntities() {
        log.info("Fetching all CTF entities");
        return ctfEntityRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CTFEntity> findAllWithFlags() {
        log.info("Fetching all CTF entities w/ flags");
        return ctfEntityRepository.findAllWithFlags();
    }

    @Transactional
    public void deleteCTFEntity(String id) {
        log.info("Deleting CTF entity with id: {}", id);

        if (!ctfEntityRepository.existsById(id)) {
            throw new ResourceNotFoundException("CTF entity not found with id: " + id);
        }

        // Answers point at the challenge; their hint usages go with them
        ctfAnswerRepository.deleteAll(ctfAnswerRepository.findByCtfEntityId(id));
        ctfEntityRepository.deleteById(id);
        log.info("Successfully deleted CTF entity with id: {}", id);
    }

    public boolean existsById(String id) {
        return ctfEntityRepository.existsById(id);
    }

    @Transactional
    public List<Flag> saveFlags(List<Flag> flags) {
        if (CollectionUtils.isEmpty(flags)) {
            return List.of();
        }

        return flagRepository.saveAll(flags);
    }

    /** Deletes every challenge in a room, with the answers players gave. */
    @Transactional
    public List<CTFEntity> deleteByRoomId(String roomId) {
        List<CTFEntity> challenges = ctfEntityRepository.findByRoomId(roomId);
        challenges.forEach(challenge -> ctfAnswerRepository.deleteAll(ctfAnswerRepository.findByCtfEntityId(challenge.getId())));
        ctfEntityRepository.deleteAll(challenges);
        return challenges;
    }

    /**
     * Updates hints in place by id so hints players already paid for keep their history. Hints left
     * out are removed together with their usage records; points already spent are not refunded.
     */
    private void syncHints(CTFEntity existingEntity, List<Hint> incoming) {
        Map<String, Hint> current = new HashMap<>();
        existingEntity.getHints().forEach(hint -> current.put(hint.getId(), hint));

        List<Hint> next = new ArrayList<>();
        int order = 0;
        for (Hint source : incoming) {
            if (source == null || source.getHint() == null || source.getHint().isBlank()) {
                continue;
            }
            Hint hint = source.getId() != null ? current.remove(source.getId()) : null;
            if (hint == null) {
                hint = new Hint();
                hint.setCtfEntity(existingEntity);
            }
            hint.setHint(source.getHint().trim());
            hint.setCost(source.getCost() == null ? 0 : Math.max(0, source.getCost()));
            hint.setUnlockAfterAttempts(source.getUnlockAfterAttempts() == null ? 0 : Math.max(0, source.getUnlockAfterAttempts()));
            hint.setOrderIndex(order++);
            next.add(hint);
        }

        if (!current.isEmpty()) {
            hintUsageRepository.deleteByHintIdIn(new ArrayList<>(current.keySet()));
            existingEntity.getHints().removeAll(current.values());
            hintRepository.deleteAll(current.values());
        }
        existingEntity.getHints().clear();
        existingEntity.getHints().addAll(next);
    }

    // Private helper methods

    private void validateCTFEntity(CTFEntity ctfEntity) {
        if (ctfEntity == null) {
            throw new IllegalArgumentException("CTF entity cannot be null");
        }

        if (ctfEntity.getQuestion() == null || ctfEntity.getQuestion().trim().isEmpty()) {
            throw new IllegalArgumentException("CTF entity name cannot be null or empty");
        }
    }

    private void processAndSaveFlags(CTFEntity ctfEntity) {
        List<Flag> flags = ctfEntity.getFlags();

        flags.forEach(flag -> {
            flag.setCtfEntity(ctfEntity);
            flagRepository.save(flag);
        });

        log.info("Processed and saved {} flags for CTF entity: {}", flags.size(), ctfEntity.getId());
    }

    private void updateEntityFields(CTFEntity existingEntity, CTFEntity updatedEntity) {
        Optional.ofNullable(updatedEntity.getQuestion()).ifPresent(existingEntity::setQuestion);
        Optional.ofNullable(updatedEntity.getMaxAttempts()).ifPresent(existingEntity::setMaxAttempts);
        Optional.ofNullable(updatedEntity.getDescription()).ifPresent(existingEntity::setDescription);
        Optional.ofNullable(updatedEntity.getCategory()).ifPresent(existingEntity::setCategory);
        Optional.ofNullable(updatedEntity.getDifficultyLevel()).ifPresent(existingEntity::setDifficultyLevel);
        Optional.ofNullable(updatedEntity.getCtfType()).ifPresent(existingEntity::setCtfType);
        Optional.ofNullable(updatedEntity.getPoints()).ifPresent(existingEntity::setPoints);
        Optional.ofNullable(updatedEntity.getAuthor()).ifPresent(existingEntity::setAuthor);
        Optional.ofNullable(updatedEntity.getTags()).ifPresent(existingEntity::setTags);
        Optional.ofNullable(updatedEntity.getVisible()).ifPresent(existingEntity::setVisible);
        Optional.ofNullable(updatedEntity.getIsActive()).ifPresent(existingEntity::setIsActive);
        Optional.ofNullable(updatedEntity.getReleaseDate()).ifPresent(existingEntity::setReleaseDate);
        // An update without an expiry clears it, so a challenge can go back to staying open
        existingEntity.setExpirationDate(updatedEntity.getExpirationDate());
        Optional.ofNullable(updatedEntity.getAttachments()).ifPresent(existingEntity::setAttachments);
        Optional.ofNullable(updatedEntity.getSolutionExplanation()).ifPresent(existingEntity::setSolutionExplanation);
        Optional.ofNullable(updatedEntity.getRelatedChallengeIds()).ifPresent(existingEntity::setRelatedChallengeIds);

        if (updatedEntity.getHints() != null) {
            syncHints(existingEntity, updatedEntity.getHints());
        }

        if (!CollectionUtils.isEmpty(updatedEntity.getFlags())) {
            // Replace in place so orphanRemoval deletes flags that were removed
            existingEntity.getFlags().clear();
            updatedEntity.getFlags().forEach(source -> {
                Flag flag = new Flag();
                flag.setFlag(source.getFlag());
                flag.setSurroundWithTag(source.getSurroundWithTag());
                flag.setCaseSensitive(source.getCaseSensitive());
                flag.setWeight(source.getWeight());
                flag.setCtfEntity(existingEntity);
                existingEntity.getFlags().add(flag);
            });
        }
    }
}