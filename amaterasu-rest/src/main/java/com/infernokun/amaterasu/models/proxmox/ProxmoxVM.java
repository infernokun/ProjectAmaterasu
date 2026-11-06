package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.util.Map;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVM {
    private int vmid;
    private String name;
    private String status;
    private long uptime;
    private long mem;
    private long maxmem;
    private double cpu;
    private int cpus;
    @JsonProperty("template")
    @Builder.Default
    private boolean template = false; // Default to false

    // Set by the deploy and kept across status reads; PVE never sends these
    public static final String BASTION = "bastion";
    /** "bastion" for the lab's way in from the LAN, else null. */
    private String role;
    /** The template's cloud-init user, who logs in with the copy's password. */
    private String ciuser;
    /** The template enables the guest agent, so readiness is tracked. */
    private boolean guestAgent;
    /** Whether the guest agent answers; null when it is not tracked. */
    private Boolean ready;
    /** A bastion's IPv4 on its uplink, from the guest agent. */
    private String accessAddress;
    /** The bridge a bastion's uplink stays on, e.g. vmbr0. */
    private String uplinkBridge;
    /** The lab network behind a bastion, e.g. 172.16.0.0/24. */
    private String labSubnet;
    /** A bastion's NIC MACs as PVE generated them, by NIC (net0, net1, ...). */
    private Map<String, String> macs;

    public ProxmoxVM(Integer vmid, ProxmoxVM proxmoxVM) {
        this.vmid = vmid;
        this.name = proxmoxVM.name;
        this.status = proxmoxVM.status;
        this.uptime = proxmoxVM.uptime;
        this.mem = proxmoxVM.mem;
        this.maxmem = proxmoxVM.maxmem;
        this.cpu = proxmoxVM.cpu;
        this.cpus = proxmoxVM.cpus;
        this.template = proxmoxVM.template;
    }

    @JsonIgnore
    public boolean isBastion() {
        return BASTION.equals(role);
    }

    /** Copies what the deploy and guest agent learned from the tracked copy of this VM. */
    public ProxmoxVM keepLabDetails(ProxmoxVM tracked) {
        if (tracked != null) {
            role = tracked.role;
            ciuser = tracked.ciuser;
            guestAgent = tracked.guestAgent;
            ready = tracked.ready;
            accessAddress = tracked.accessAddress;
            uplinkBridge = tracked.uplinkBridge;
            labSubnet = tracked.labSubnet;
            macs = tracked.macs;
        }
        if (guestAgent && !"running".equals(status)) {
            ready = false;
        }
        return this;
    }

    @JsonSetter("template")
    public void setTemplate(Integer template) {
        this.template = template != null && template == 1;
    }

    @JsonGetter("template")
    public int getTemplateAsInt() { // Force 1 or omit
        return template ? 1 : 0;
    }

    public String toJsonString() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }


}