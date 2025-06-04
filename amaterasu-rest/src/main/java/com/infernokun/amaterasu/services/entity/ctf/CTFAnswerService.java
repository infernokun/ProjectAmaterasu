package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ChallengeNotAnsweredException;
import com.infernokun.amaterasu.models.entities.ctf.CTFAnswer;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CTFAnswerService {
    private final CTFAnswerRepository ctfAnswerRepository;

    public CTFAnswer findByRoomUserIdAndCtfEntityId(String roomUserId, String ctfEntityId){
        return ctfAnswerRepository.findByRoomUserIdAndCtfEntityId(roomUserId, ctfEntityId)
                .orElseThrow(() -> new ChallengeNotAnsweredException("Challenge not yet answered"));
    }

    public Optional<CTFAnswer> findByRoomUserIdAndCtfEntityIdOptional(String roomUserId, String ctfEntityId){
        return ctfAnswerRepository.findByRoomUserIdAndCtfEntityId(roomUserId, ctfEntityId);
    }

    public CTFAnswer saveAnsweredCTFEntity(CTFAnswer CTFAnswer) {
        return ctfAnswerRepository.save(CTFAnswer);
    }
}
