package com.infernokun.amaterasu.models.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxResponse {
    private List<ProxmoxVM> data;
}