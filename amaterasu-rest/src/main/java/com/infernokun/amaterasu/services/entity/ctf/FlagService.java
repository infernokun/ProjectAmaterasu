package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.entities.ctf.dto.CTFAnswerRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.AnsweredCTFEntityResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.JoinRoomResponse;
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
    private final CTFAnswerService ctfAnswerService;
    private final FlagRepository flagRepository;
    private final ModelMapper modelMapper;
    private final RoomUserService roomUserService;
    private final RoomService roomService;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    public boolean validateFlag(CTFAnswerRequest ctfAnswerRequest) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(ctfAnswerRequest.getQuestionId());

        return challengeFlags.stream().map(Flag::getFlag)
                .anyMatch(flag -> flag.equals(ctfAnswerRequest.getFlag()));
    }

    public AnsweredCTFEntityResponse addAnsweredCTFEntity(String userId, CTFAnswerRequest ctfAnswerRequest, boolean correct) {
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(ctfAnswerRequest.getRoomId());
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityByIdWithFlags(ctfAnswerRequest.getQuestionId());
        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);

        CTFAnswer ctfAnswer = ctfAnswerService
                .findByUserIdAndCtfEntityIdOptional(user.getId(), ctfEntity.getId())
                .orElseGet(() -> CTFAnswer
                        .builder()
                        .user(user)
                        .ctfEntity(ctfEntity)
                        .correct(correct)
                        .answers(new ArrayList<>())
                        .attemptTimes(new ArrayList<>())
                        .attempts(0)
                        .build());

        ctfAnswer.getAnswers().add(ctfAnswerRequest);
        ctfAnswer.getAttemptTimes().add(LocalDateTime.now());
        ctfAnswer.setAttempts(ctfAnswer.getAttempts() + 1);
        ctfAnswer.setCorrect(correct);

        ctfAnswer = ctfAnswerService.saveAnsweredCTFEntity(ctfAnswer);

        AnsweredCTFEntityResponse answeredCTFEntityResponse = modelMapper.map(ctfAnswer,
                AnsweredCTFEntityResponse.class);

        answeredCTFEntityResponse.setCtfEntity(modelMapper.map(ctfAnswer.getCtfEntity(),
                CTFEntityResponse.class));





            roomUserOpt.ifPresent(roomUser -> {
                if (correct) {
                    int points = ctfEntity.getPoints();
                    roomUser.setPoints(roomUser.getPoints() + points);
                }

                roomUser = roomUserService.save(roomUser);

                answeredCTFEntityResponse.setJoinRoomResponse(JoinRoomResponse.builder()
                        .roomId(ctfAnswerRequest.getRoomId())
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
