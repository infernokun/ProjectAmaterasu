package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxNetwork {
    private String iface;
    private String address;
    private String cidr;
}
