package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.entity.ctf.CTFAnswerService;
import com.infernokun.amaterasu.services.entity.ctf.CTFEntityService;
import com.infernokun.amaterasu.services.entity.ctf.FlagService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.ctf.RoomUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock private CTFEntityService ctfEntityService;
    @Mock private UserService userService;
    @Mock private CTFAnswerService ctfAnswerService;
    @Mock private FlagRepository flagRepository;
    @Mock private RoomUserService roomUserService;
    @Mock private RoomService roomService;

    private FlagService flagService;
    private User user;
    private Room room;
    private RoomUser roomUser;
    private CTFEntity challenge;

    @BeforeEach
    void setUp() {
        flagService = new FlagService(ctfEntityService, userService, ctfAnswerService, flagRepository,
                new ModelMapper(), roomUserService, roomService);

        user = new User();
        user.setId("user1");

        room = new Room();
        room.setId("room1");

        roomUser = new RoomUser();
        roomUser.setId("ru1");
        roomUser.setUser(user);
        roomUser.setRoom(room);
        roomUser.setPoints(0);

        challenge = new CTFEntity();
        challenge.setId("q1");
        challenge.setRoom(room);
        challenge.setPoints(50);
        challenge.setMaxAttempts(2);

        lenient().when(userService.findUserById("user1")).thenReturn(user);
        lenient().when(roomService.findByRoomId("room1")).thenReturn(room);
        lenient().when(ctfEntityService.findCTFEntityByIdWithFlags("q1")).thenReturn(challenge);
        lenient().when(roomUserService.findByUserAndRoom(user, room)).thenReturn(Optional.of(roomUser));
        lenient().when(roomUserService.save(any(RoomUser.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ctfAnswerService.saveAnsweredCTFEntity(any(CTFEntityAnswer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CTFEntityAnswerRequest request(String flag) {
        return CTFEntityAnswerRequest.builder().flag(flag).questionId("q1").roomId("room1").userId("user1").build();
    }

    private CTFEntityAnswer existingAnswer(boolean correct, int attempts) {
        return CTFEntityAnswer.builder()
                .roomUser(roomUser)
                .ctfEntity(challenge)
                .correct(correct)
                .attempts(attempts)
                .answers(new ArrayList<>())
                .attemptTimes(new ArrayList<>())
                .build();
    }

    @Test
    void validateFlagHonorsCaseSensitivity() {
        Flag sensitive = new Flag();
        sensitive.setFlag("Secret");
        sensitive.setCaseSensitive(true);
        when(flagRepository.getFlagsByCtfEntityId("q1")).thenReturn(List.of(sensitive));

        assertThat(flagService.validateFlag(request(" Secret "))).isTrue();
        assertThat(flagService.validateFlag(request("secret"))).isFalse();

        sensitive.setCaseSensitive(false);
        assertThat(flagService.validateFlag(request("SECRET"))).isTrue();
    }

    @Test
    void awardsPointsOnlyOnFirstCorrectAnswer() {
        when(ctfAnswerService.findByRoomUserIdAndCtfEntityIdOptional("ru1", "q1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAnswer(true, 1)));

        flagService.addAnsweredCTFEntity("user1", request("flag"), true);
        flagService.addAnsweredCTFEntity("user1", request("flag"), true);

        assertThat(roomUser.getPoints()).isEqualTo(50);
        verify(roomUserService, times(1)).save(roomUser);
    }

    @Test
    void rejectsAnswerAfterMaxAttempts() {
        when(ctfAnswerService.findByRoomUserIdAndCtfEntityIdOptional("ru1", "q1"))
                .thenReturn(Optional.of(existingAnswer(false, 2)));

        assertThatThrownBy(() -> flagService.addAnsweredCTFEntity("user1", request("flag"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum attempts");
        verify(ctfAnswerService, never()).saveAnsweredCTFEntity(any());
    }

    @Test
    void rejectsChallengeFromAnotherRoom() {
        Room otherRoom = new Room();
        otherRoom.setId("room2");
        challenge.setRoom(otherRoom);

        assertThatThrownBy(() -> flagService.addAnsweredCTFEntity("user1", request("flag"), true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ctfAnswerService, never()).saveAnsweredCTFEntity(any());
    }
}
