package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntityHintUsage;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import lombok.Data;

import java.util.List;

@Data
public class CTFEntityHintResponse {
    private JoinRoomResponse joinRoomResponse;
    private String ctfEntityId;
    private Hint requestedHint;
    private List<CTFEntityHintUsage> hintsUsed;
}
