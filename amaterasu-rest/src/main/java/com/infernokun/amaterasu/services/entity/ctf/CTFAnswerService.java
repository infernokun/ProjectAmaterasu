package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CTFAnswerService {
    private final CTFAnswerRepository ctfAnswerRepository;

    public CTFEntityAnswer findByRoomUserIdAndCtfEntityId(RoomUser roomUser, CTFEntity ctfEntity){
        return ctfAnswerRepository.findByRoomUserIdAndCtfEntityId(roomUser.getId(), ctfEntity.getId())
                .orElse(CTFEntityAnswer.builder()
                        .ctfEntity(ctfEntity)
                        .roomUser(roomUser)
                        .attempts(0)
                        .answers(new ArrayList<>())
                        .attemptTimes(new ArrayList<>())
                        .solvedAt(null)
                        .lastAttemptAt(null)
                        .score(0)
                        .hintUsages(new ArrayList<>())
                        .solveTimeSeconds(0L)
                        .build());
    }

    public Optional<CTFEntityAnswer> findByRoomUserIdAndCtfEntityIdOptional(String roomUserId, String ctfEntityId){
        return ctfAnswerRepository.findByRoomUserIdAndCtfEntityId(roomUserId, ctfEntityId);
    }

    public CTFEntityAnswer saveAnsweredCTFEntity(CTFEntityAnswer CTFEntityAnswer) {
        return ctfAnswerRepository.save(CTFEntityAnswer);
    }
}
