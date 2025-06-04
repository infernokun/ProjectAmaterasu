package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import com.infernokun.amaterasu.repositories.ctf.HintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HintService {
    private final HintRepository hintRepository;
    private final CTFEntityService ctfEntityService;

    public Hint getHintById(String hintId) {
        return hintRepository.findById(hintId).orElseThrow(()
                -> new ResourceNotFoundException("Hint not found"));
    }

    // Unlock a hint manually
    public Hint unlockHint(String hintId) {
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new ResourceNotFoundException("Hint not found"));

        hint.setIsUnlocked(true);
        return hintRepository.save(hint);
    }

    // Use a hint (mark as used and deduct points)
    public Hint useHint(String hintId, Integer userPoints) {
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new ResourceNotFoundException("Hint not found"));

        if (!hint.getIsUnlocked()) {
            throw new IllegalStateException("Hint is not unlocked yet");
        }

        if (hint.getUsedAt() != null) {
            throw new IllegalStateException("Hint has already been used");
        }

        if (userPoints < hint.getCost()) {
            throw new IllegalStateException("Not enough points to use this hint");
        }

        hint.setUsedAt(LocalDateTime.now());
        hint.setPointsDeducted(hint.getCost());

        return hintRepository.save(hint);
    }

    public Hint save(Hint hint) {
        return hintRepository.save(hint);
    }

    // Check if a hint can be used
    public boolean canUseHint(String hintId, Integer userPoints) {
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new ResourceNotFoundException("Hint not found"));

        return hint.getIsUnlocked() &&
                hint.getUsedAt() == null &&
                userPoints >= hint.getCost();
    }

    // Create a new hint
    public Hint createHint(String hintText, Integer orderIndex, Integer cost,
                           Integer unlockAfterAttempts, String ctfEntityId) {
        assert unlockAfterAttempts != null;

        Hint hint = new Hint();
        hint.setHint(hintText);
        hint.setOrderIndex(orderIndex);
        hint.setCost(cost != null ? cost : 0);
        hint.setUnlockAfterAttempts(unlockAfterAttempts);
        hint.setIsUnlocked(unlockAfterAttempts == 0);

        hint.setCtfEntity(ctfEntityService.findCTFEntityById(ctfEntityId));

        return hintRepository.save(hint);
    }

    // Update hint order
    public void updateHintOrder(String hintId, Integer newOrderIndex) {
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new ResourceNotFoundException("Hint not found"));

        hint.setOrderIndex(newOrderIndex);
        hintRepository.save(hint);
    }

    // Delete hint
    public void deleteHint(String hintId) {
        if (!hintRepository.existsById(hintId)) {
            throw new ResourceNotFoundException("Hint not found");
        }
        hintRepository.deleteById(hintId);
    }

    // Helper class for statistics
    public record HintStatistics(long totalHints, long unlockedHints, long usedHints, int totalPointsDeducted) { }
}