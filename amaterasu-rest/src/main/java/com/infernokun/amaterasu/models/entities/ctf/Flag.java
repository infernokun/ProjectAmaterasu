package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "flags")
public class Flag extends StoredObject {
    private String flag;
    private Boolean surroundWithTag = false;
    private Boolean caseSensitive = true;
    private Double weight = 1.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctf_entity_id")
    @JsonBackReference
    private CTFEntity ctfEntity;
}
