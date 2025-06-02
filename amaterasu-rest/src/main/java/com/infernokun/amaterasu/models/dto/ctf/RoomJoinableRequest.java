package com.infernokun.amaterasu.models.dto.ctf;

import lombok.Data;

import java.util.List;

@Data
public class RoomJoinableRequest {
    private String userId;
    private List<String> roomIds;
}