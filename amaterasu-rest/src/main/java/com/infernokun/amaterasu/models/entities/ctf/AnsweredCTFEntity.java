package com.infernokun.amaterasu.models.entities.ctf;

import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "answered_ctf_entity")
public class AnsweredCTFEntity extends StoredObject {
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne
    @JoinColumn(name = "ctf_entity_id")
    private CTFEntity ctfEntity;
    @Builder.Default
    private List<String> answers = new ArrayList<>();
    @Builder.Default
    private List<LocalDateTime> times = new ArrayList<>();
    private int attempts;
    @Builder.Default
    private boolean correct = false;
}