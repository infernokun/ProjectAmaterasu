package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.dto.ctf.FlagAnswerRequest;
import com.infernokun.amaterasu.models.dto.ctf.AnsweredCTFEntityResponse;
import com.infernokun.amaterasu.models.dto.ctf.CTFEntityResponse;
import com.infernokun.amaterasu.models.dto.ctf.JoinRoomResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.*;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.UserService;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FlagService {
    private final CTFEntityService ctfEntityService;
    private final UserService userService;
    private final AnsweredCTFEntityService answeredCTFEntityService;
    private final FlagRepository flagRepository;
    private final ModelMapper modelMapper;
    private final RoomUserService roomUserService;
    private final RoomService roomService;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    public boolean validateFlag(FlagAnswerRequest flagAnswerRequest) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(flagAnswerRequest.getQuestionId());

        return challengeFlags.stream().map(Flag::getFlag)
                .anyMatch(flag -> flag.equals(flagAnswerRequest.getFlag()));
    }

    public AnsweredCTFEntityResponse addAnsweredCTFEntity(String userId, FlagAnswerRequest flagAnswerRequest, boolean correct) {
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(flagAnswerRequest.getRoomId());
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityByIdWithFlags(flagAnswerRequest.getQuestionId());
        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);

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





            roomUserOpt.ifPresent(roomUser -> {
                if (correct) {
                    int points = ctfEntity.getPoints();
                    roomUser.setPoints(roomUser.getPoints() + points);
                }

                roomUser = roomUserService.save(roomUser);

                answeredCTFEntityResponse.setJoinRoomResponse(JoinRoomResponse.builder()
                        .roomId(flagAnswerRequest.getRoomId())
                        .points(roomUser.getPoints())
                        .userId(roomUser.getUser().getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                        .build());
            });

        return answeredCTFEntityResponse;
    }

    public List<Flag> getFlagsByCtfEntityId(String ctfEntityId) {
        return flagRepository.getFlagsByCtfEntityId(ctfEntityId);
    }
}
