package com.infernokun.amaterasu.models.dto.ctf;

import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomResponse {
    private String roomId;
    private String userId;
    private Integer points;
    private RoomUserStatus roomUserStatus;
}