package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.TeamResolver;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CTFAnswerService {
    private final CTFAnswerRepository ctfAnswerRepository;
    private final TeamResolver teamResolver;

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

    /** A teammate's correct answer to the question, if one of them solved it. Teams are the lab's: see TeamResolver. */
    public Optional<CTFEntityAnswer> solvedByTeammate(RoomUser roomUser, CTFEntity ctfEntity) {
        Room room = roomUser.getRoom() != null ? roomUser.getRoom() : ctfEntity.getRoom();
        Team team = teamResolver.teamOf(room, roomUser.getUser());
        if (team == null) {
            return Optional.empty();
        }
        Map<String, Team> teams = teamResolver.teamsByUser(room);
        return ctfAnswerRepository.findByCtfEntityId(ctfEntity.getId()).stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getCorrect()) && !answer.getRoomUser().getId().equals(roomUser.getId())
                        && answer.getRoomUser().getUser() != null
                        && teams.get(answer.getRoomUser().getUser().getId()) != null
                        && teams.get(answer.getRoomUser().getUser().getId()).getId().equals(team.getId()))
                .findFirst();
    }

    public List<CTFEntityAnswer> findByRoomUserId(RoomUser roomUser) {
        return ctfAnswerRepository.findByRoomUserId(roomUser.getId());
    }

    public Optional<CTFEntityAnswer> findByRoomUserIdAndCtfEntityIdOptional(String roomUserId, String ctfEntityId){
        return ctfAnswerRepository.findByRoomUserIdAndCtfEntityId(roomUserId, ctfEntityId);
    }

    public CTFEntityAnswer saveAnsweredCTFEntity(CTFEntityAnswer CTFEntityAnswer) {
        return ctfAnswerRepository.save(CTFEntityAnswer);
    }
}
