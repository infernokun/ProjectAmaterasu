package com.infernokun.amaterasu.models.dto.ctf;

import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinRoomResponse {
    private String roomId;
    private String userId;
    private RoomUserStatus roomUserStatus;
}