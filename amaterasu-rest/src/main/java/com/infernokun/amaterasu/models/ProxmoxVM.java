package com.infernokun.amaterasu.models;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    @JsonProperty("template")
    private boolean template = false; // Default to false

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