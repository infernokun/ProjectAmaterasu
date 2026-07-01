package com.infernokun.amaterasu.models;

import com.infernokun.amaterasu.models.proxmox.LabNetworkConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LabActionRequest {
    private String labId;
    private String userId;
    private String teamId;
    private String labTrackerId;
    private String remoteServerId;
    /** Optional per-VM bridge + IP assignments chosen in the deploy dialog (Proxmox labs). */
    private List<LabNetworkConfig> networkConfig = new ArrayList<>();
}
