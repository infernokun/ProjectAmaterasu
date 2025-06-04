package com.infernokun.amaterasu.models.entities.ctf.dto;

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