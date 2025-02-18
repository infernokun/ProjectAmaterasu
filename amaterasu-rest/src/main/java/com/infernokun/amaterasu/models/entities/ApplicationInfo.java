package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "application_info")
public class ApplicationInfo extends StoredObject {
    private String name = "Project Amaterasu";
    private String description = "A lab orchestrator.";
    @Column(columnDefinition = "TEXT")
    private String settings = "{}";
}
