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
    private Map<String, String> networks = new HashMap<>();

    @JsonAnySetter
    public void setDynamicProperties(String key, Object value) {
        if (key.startsWith("net")) {
            networks.put(key, value.toString());
        }
    }
}
