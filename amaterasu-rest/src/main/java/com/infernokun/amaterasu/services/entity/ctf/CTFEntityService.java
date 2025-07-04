package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CTFEntityService {
    private final CTFEntityRepository ctfEntityRepository;
    private final FlagRepository flagRepository;

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

        // Handle flags update if needed
        if (!CollectionUtils.isEmpty(updatedEntity.getFlags())) {
            // You might want to implement a more sophisticated flag update logic here
            existingEntity.setFlags(updatedEntity.getFlags());
        }
    }
}