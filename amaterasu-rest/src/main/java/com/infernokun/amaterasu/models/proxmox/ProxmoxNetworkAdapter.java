package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * A selectable network adapter (bridge) surfaced to the deploy dialog. Derived from
 * the node's network list; {@code gateway} is the bridge's own address and
 * {@code availableIpCount} a best-effort count of free host addresses on it.
 */
@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxNetworkAdapter {
    private String iface;
    private String cidr;
    private String gateway;
    private Integer availableIpCount;
}
