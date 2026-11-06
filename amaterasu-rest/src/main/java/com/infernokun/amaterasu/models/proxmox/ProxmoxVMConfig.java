package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVMConfig {
    public static final String BASTION_TAG = "bastion";
    /** On a bastion template this NIC keeps its own bridge: the uplink players SSH in through. */
    public static final String UPLINK_NIC = "net0";

    private String name;
    /** PVE separates tags with ';' and accepts ',' or spaces on input. */
    private String tags;
    /** The cloud-init user. Clones of a template that has one get the copy's password. */
    private String ciuser;
    /** "1", "0" or a property string such as "enabled=1,fstrim_cloned_disks=1". */
    private String agent;
    private Map<String, String> networks = new HashMap<>();
    private Map<String, String> ipconfigs = new HashMap<>();

    @JsonAnySetter
    public void setDynamicProperties(String key, Object value) {
        if (value == null) {
            return;
        }
        if (key.matches("net\\d+")) {
            networks.put(key, value.toString());
        } else if (key.matches("ipconfig\\d+")) {
            ipconfigs.put(key, value.toString());
        }
    }

    @JsonIgnore
    public List<String> getTagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.trim().split("[;,\\s]+")).filter(tag -> !tag.isBlank()).toList();
    }

    @JsonIgnore
    public boolean isBastion() {
        return getTagList().stream().anyMatch(BASTION_TAG::equalsIgnoreCase);
    }

    /** The NIC keeps its template bridge instead of moving to the lab bridge. */
    public boolean isUplink(String nic) {
        return isBastion() && UPLINK_NIC.equals(nic);
    }

    @JsonIgnore
    public boolean isAgentEnabled() {
        if (agent == null || agent.isBlank()) {
            return false;
        }
        for (String part : agent.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 1 || kv[0].equals("enabled")) {
                String value = kv[kv.length - 1].trim();
                return value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                        || value.equalsIgnoreCase("yes");
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean hasCiuser() {
        return ciuser != null && !ciuser.isBlank();
    }
}
