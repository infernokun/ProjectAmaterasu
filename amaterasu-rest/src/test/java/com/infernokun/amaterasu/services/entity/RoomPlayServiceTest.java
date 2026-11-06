package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomStandings;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.services.entity.ctf.CTFEntityService;
import com.infernokun.amaterasu.services.entity.ctf.RoomPlayService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.ctf.RoomUserService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomPlayServiceTest {
    private final RoomService roomService = mock(RoomService.class);
    private final RoomUserService roomUserService = mock(RoomUserService.class);
    private final CTFAnswerRepository answers = mock(CTFAnswerRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoomPlayService service = new RoomPlayService(roomService, roomUserService, mock(CTFEntityService.class),
            answers, users);

    private RoomUser player(String name, Team team, LocalDateTime joined, int... totals) {
        User user = new User(name, "x");
        user.setId(name);
        user.setTeam(team);
        RoomUser roomUser = RoomUser.builder().user(user).roomUserStatus(RoomUserStatus.JOINED).points(0)
                .pointsHistory(new ArrayList<>()).build();
        roomUser.setId("ru-" + name);
        roomUser.setCreatedAt(joined);
        LocalDateTime at = joined.withNano(0);
        int previous = 0;
        for (int total : totals) {
            roomUser.getPointsHistory().add(new PointsHistoryEntry(at, total, total - previous, "solve"));
            previous = total;
            at = at.plusMinutes(1);
        }
        roomUser.setPoints(previous);
        return roomUser;
    }

    @Test
    void ranksByPointsThenByWhoGotThereFirstAndSumsTeams() {
        Team red = new Team();
        red.setId("red");
        red.setName("Red Fox");
        LocalDateTime joined = LocalDateTime.of(2026, 11, 4, 9, 0, 0, 500_000_000);
        RoomUser late = player("late", red, joined, 100, 200);
        RoomUser early = player("early", red, joined, 200);
        RoomUser solo = player("solo", null, joined, 50);
        Room room = Room.builder().name("Winter Range").build();
        room.setId("r1");
        room.setCreatedAt(LocalDateTime.of(2026, 11, 4, 8, 30));
        when(users.countEnabledByTeam()).thenReturn(List.<Object[]>of(new Object[]{"red", 4L}));
        when(roomService.findByRoomId("r1")).thenReturn(room);
        when(roomUserService.findByRoomIdOrderByPointsDesc("r1")).thenReturn(List.of(late, early, solo));
        when(answers.findByRoomUserRoomId("r1")).thenReturn(List.of());

        RoomStandings standings = service.standings("r1");

        assertThat(standings.players()).extracting(RoomStandings.Player::username).containsExactly("early", "late", "solo");
        assertThat(standings.players().get(0).history().getFirst().total()).isZero();
        // The starting zero comes before the first change even though it was stored with sub-second precision
        assertThat(standings.players().get(0).history().getFirst().at())
                .isBeforeOrEqualTo(standings.players().get(0).history().get(1).at());
        assertThat(standings.teams()).hasSize(1);
        assertThat(standings.teams().getFirst().points()).isEqualTo(400);
        assertThat(standings.teams().getFirst().history().getLast().total()).isEqualTo(400);
        // Two of Red Fox's four members joined this room
        assertThat(standings.teams().getFirst().members()).isEqualTo(2);
        assertThat(standings.teams().getFirst().teamSize()).isEqualTo(4);
        assertThat(standings.openedAt()).isEqualTo(room.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
    }
}
