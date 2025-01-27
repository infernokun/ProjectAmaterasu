package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "team")
public class Team extends StoredObject {
    private String name;
    private String description;
}
