package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.dto.ctf.FlagAnswerRequest;
import com.infernokun.amaterasu.models.dto.ctf.AnsweredCTFEntityResponse;
import com.infernokun.amaterasu.models.dto.ctf.CTFEntityResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.AnsweredCTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.UserService;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

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
    private final ModelMapper modelMapper;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    public boolean validateFlag(FlagAnswerRequest flagAnswerRequest) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(flagAnswerRequest.getQuestionId());

        return challengeFlags.stream().map(Flag::getFlag)
                .anyMatch(flag -> flag.equals(flagAnswerRequest.getFlag()));
    }

    public AnsweredCTFEntityResponse addAnsweredCTFEntity(String userId, FlagAnswerRequest flagAnswerRequest, boolean correct) {
        User user = this.userService.findUserById(userId);
        CTFEntity ctfEntity = this.ctfEntityService.findCTFEntityByIdWithFlags(flagAnswerRequest.getQuestionId());

        AnsweredCTFEntity answeredCTFEntity = answeredCTFEntityService
                .findByUserIdAndCtfEntityIdOptional(user.getId(), ctfEntity.getId())
                .orElseGet(() -> AnsweredCTFEntity
                        .builder()
                        .user(user)
                        .ctfEntity(ctfEntity)
                        .correct(correct)
                        .answers(new ArrayList<>())
                        .attemptTimes(new ArrayList<>())
                        .attempts(0)
                        .build());

        answeredCTFEntity.getAnswers().add(flagAnswerRequest);
        answeredCTFEntity.getAttemptTimes().add(LocalDateTime.now());
        answeredCTFEntity.setAttempts(answeredCTFEntity.getAttempts() + 1);
        answeredCTFEntity.setCorrect(correct);

        answeredCTFEntity = answeredCTFEntityService.saveAnsweredCTFEntity(answeredCTFEntity);

        AnsweredCTFEntityResponse answeredCTFEntityResponse = modelMapper.map(answeredCTFEntity,
                AnsweredCTFEntityResponse.class);

        answeredCTFEntityResponse.setCtfEntity(modelMapper.map(answeredCTFEntity.getCtfEntity(),
                CTFEntityResponse.class));

        return answeredCTFEntityResponse;
    }

    public List<Flag> getFlagsByCtfEntityId(String ctfEntityId) {
        return flagRepository.getFlagsByCtfEntityId(ctfEntityId);
    }
}
