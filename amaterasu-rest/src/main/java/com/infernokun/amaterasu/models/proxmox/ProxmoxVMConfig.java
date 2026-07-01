package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVMConfig {
    private String name;
    /** Network adapter definitions, e.g. net0 -> "virtio=MAC,bridge=vmbr0,firewall=1". */
    private Map<String, String> networks = new HashMap<>();
    /** Cloud-init IP configuration, e.g. ipconfig0 -> "ip=10.0.0.5/24,gw=10.0.0.1". */
    private Map<String, String> ipConfigs = new HashMap<>();

    @JsonAnySetter
    public void setDynamicProperties(String key, Object value) {
        if (value == null) {
            return;
        }
        // ipconfig0/ipconfig1/... must be checked first: they also start with the
        // "net"-adjacent prefix space but carry the IP assignment, which we previously
        // dropped on the floor. Capturing them separately lets the deploy path both
        // read and re-apply per-VM addressing.
        if (key.startsWith("ipconfig")) {
            ipConfigs.put(key, value.toString());
        } else if (key.startsWith("net")) {
            networks.put(key, value.toString());
        }
    }
}
