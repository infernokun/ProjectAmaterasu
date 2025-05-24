package com.infernokun.amaterasu.services.ctf;

import com.infernokun.amaterasu.exceptions.ChallengeNotAnsweredException;
import com.infernokun.amaterasu.models.entities.ctf.AnsweredCTFEntity;
import com.infernokun.amaterasu.repositories.ctf.AnsweredCTFEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnsweredCTFEntityService {
    private final AnsweredCTFEntityRepository answeredCTFEntityRepository;

    public Optional<AnsweredCTFEntity> findByUserIdAndCtfEntityIdOptional(String userId, String ctfEntityId) {
        return answeredCTFEntityRepository.findByUserIdAndCtfEntityId(userId, ctfEntityId);
    }

    public AnsweredCTFEntity findByUserIdAndCtfEntityId(String userId, String ctfEntityId) {
        return answeredCTFEntityRepository.findByUserIdAndCtfEntityId(userId, ctfEntityId)
                .orElseThrow(() -> new ChallengeNotAnsweredException("Challenge not yet answered"));
    }

    public AnsweredCTFEntity saveAnsweredCTFEntity(AnsweredCTFEntity answeredCTFEntity) {
        return answeredCTFEntityRepository.save(answeredCTFEntity);
    }
}
