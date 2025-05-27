package com.infernokun.amaterasu.models.entities.lab;

import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabFileChangeLog extends StoredObject {
    @ManyToOne
    @JoinColumn(name = "lab_id")
    Lab lab;
}
