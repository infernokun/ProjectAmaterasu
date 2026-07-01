package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Per-VM network assignment chosen at lab-deploy time and persisted on the
 * {@code LabTracker} so it can be re-applied on redeploy. {@code vmid} refers to
 * the template VM the assignment applies to; {@code bridge} is the selected node
 * bridge (e.g. "vmbr0") and {@code ipAddress} the static address to hand the clone.
 */
@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabNetworkConfig {
    private Integer vmid;
    private String bridge;
    private String ipAddress;
}
