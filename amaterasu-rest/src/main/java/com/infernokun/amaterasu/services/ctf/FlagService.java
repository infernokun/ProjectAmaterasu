package com.infernokun.amaterasu.services.ctf;

import com.infernokun.amaterasu.models.dto.ctf.FlagAnswer;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.AnsweredCTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlagService {
    private final CTFEntityService ctfEntityService;
    private final UserService userService;
    private final AnsweredCTFEntityService answeredCTFEntityService;
    private final FlagRepository flagRepository;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    public boolean validateFlag(FlagAnswer flagAnswer) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(flagAnswer.getQuestionId());

        return challengeFlags.stream().map(Flag::getFlag)
                .anyMatch(flag -> flag.equals(flagAnswer.getFlag()));
    }

    public AnsweredCTFEntity addAnsweredCTFEntity(String userId, FlagAnswer flagAnswer, boolean correct) {
        User user = this.userService.findUserById(userId);
        CTFEntity ctfEntity = this.ctfEntityService.findCTFEntityByIdWithFlags(flagAnswer.getQuestionId());

        AnsweredCTFEntity answeredCTFEntity = answeredCTFEntityService
                .findByUserIdAndCtfEntityIdOptional(user.getId(), ctfEntity.getId())
                .orElseGet(() -> AnsweredCTFEntity
                        .builder()
                        .user(user)
                        .ctfEntity(ctfEntity)
                        .correct(correct)
                        .answers(new ArrayList<>())
                        .times(new ArrayList<>())
                        .attempts(0)
                        .build());

        answeredCTFEntity.getAnswers().add(flagAnswer.getFlag());
        answeredCTFEntity.getTimes().add(LocalDateTime.now());
        answeredCTFEntity.setAttempts(answeredCTFEntity.getAttempts() + 1);
        answeredCTFEntity.setCorrect(correct);

        return answeredCTFEntityService.saveAnsweredCTFEntity(answeredCTFEntity);
    }

    public List<Flag> getFlagsByCtfEntityId(String ctfEntityId) {
        return flagRepository.getFlagsByCtfEntityId(ctfEntityId);
    }
}
