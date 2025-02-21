package com.infernokun.amaterasu.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxResponse {
    private ProxmoxVM[] data;

    public ProxmoxVM[] getData() {
        return data;
    }
}