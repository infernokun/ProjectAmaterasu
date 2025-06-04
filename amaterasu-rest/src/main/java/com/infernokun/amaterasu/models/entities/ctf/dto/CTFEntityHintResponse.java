package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.entities.ctf.Hint;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CTFEntityHintResponse {
    private JoinRoomResponse joinRoomResponse;
    private String ctfEntityId;
    private Boolean correct;
    private Integer attempts;
    private LocalDateTime solvedAt;
    private LocalDateTime lastAttemptAt;
    private List<Hint> hintsUsed;
}
