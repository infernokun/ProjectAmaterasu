package com.infernokun.amaterasu.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVM {

    private String name;
    private int vmid;
    private String status;
    private long uptime;
    private long mem;
    private long maxmem;
    private double cpu;
    private int cpus;
    private boolean template;

    @JsonProperty("template")
    public void setTemplate(int template) {
        this.template = (template == 1);
    }
}