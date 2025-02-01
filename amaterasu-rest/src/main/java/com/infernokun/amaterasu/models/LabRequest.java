package com.infernokun.amaterasu.models;

import com.infernokun.amaterasu.models.entities.LabTracker;
import lombok.Data;

@Data
public class LabRequest {
    private String labId;
    private String userId;
    private String teamId;
    private String labTrackerId;
}
