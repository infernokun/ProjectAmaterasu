package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.helper.IntegerListConverter;
import jakarta.persistence.*;
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
    @Enumerated(EnumType.STRING)
    private LabStatus status = LabStatus.ACTIVE;
    private String version;
    private Integer capacity;
    @Enumerated(EnumType.STRING)
    private LabType labType;
    @Builder.Default
    private boolean ready = false;
    private String dockerFile;
    @Builder.Default
    @Column(columnDefinition = "TEXT")
    @Convert(converter = IntegerListConverter.class)
    private List<Integer> vmIds = new ArrayList<>();

    public String toJsonString() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }
}
