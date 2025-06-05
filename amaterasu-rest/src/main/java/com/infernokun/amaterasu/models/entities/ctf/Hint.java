package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "hints")
public class Hint extends StoredObject {
    private String hint;
    private Integer orderIndex;
    private Integer cost = 0;
    private Boolean isUnlocked = false;
    private Integer unlockAfterAttempts = 0;
    private LocalDateTime usedAt;
    private Integer pointsDeducted = 0;

    @ManyToOne
    @JoinColumn(name = "ctf_entity_id")
    @JsonBackReference
    private CTFEntity ctfEntity;
}