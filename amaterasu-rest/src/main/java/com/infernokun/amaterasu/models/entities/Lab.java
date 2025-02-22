package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab")
public class Lab extends StoredObject {
    private String name;
    private String description;
    @Builder.Default
    private LabStatus status = LabStatus.ACTIVE;
    private String createdBy;
    private String version;
    private Integer capacity;
    private LabType labType;
    @Builder.Default
    private boolean ready = false;
    private String dockerFile;
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private List<Integer> VMIDs = new ArrayList<>();

    public String toJsonString() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }
}
