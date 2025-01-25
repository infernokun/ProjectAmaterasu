package com.infernokun.amaterasu_rest.models.entities;

import com.infernokun.amaterasu_rest.models.enums.LabStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab")
public class Lab {
    @Id
    @UuidGenerator
    private String id;
    private String name;
    private String description;
    private LabStatus status;
    private String createdBy;
    private String version;
    private Integer capacity;
    private String dockerFile;
    private LocalDateTime createdDate = LocalDateTime.now();
    private LocalDateTime lastModifiedDate = LocalDateTime.now();
}
