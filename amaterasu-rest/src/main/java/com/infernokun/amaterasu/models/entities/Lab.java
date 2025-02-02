package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

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
    private LabStatus status;
    private String createdBy;
    private String version;
    private Integer capacity;
    private LabType labType;
    private boolean ready;
    private String dockerFile;

    public String toJsonString() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
