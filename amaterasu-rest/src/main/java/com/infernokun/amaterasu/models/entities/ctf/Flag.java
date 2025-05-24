package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
    private Boolean surroundWithTag;
    private Boolean caseSensitive;
    private Double weight;
    @ManyToOne
    @JoinColumn(name = "ctf_entity_id")
    @JsonBackReference
    private CTFEntity ctfEntity;
}
