package com.infernokun.amaterasu.models.entities.ctf;

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
@Builder
@Table(name = "ctf_hint_usage",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ctf_entity_answer_id", "hint_id"}))
public class CTFEntityHintUsage extends StoredObject {

    @ManyToOne
    @JoinColumn(name = "ctf_entity_answer_id", nullable = false)
    private CTFEntityAnswer ctfEntityAnswer;

    @ManyToOne
    @JoinColumn(name = "hint_id", nullable = false)
    private Hint hint;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    @Column(name = "points_deducted", nullable = false)
    private Integer pointsDeducted;

    @Column(name = "usage_order")
    private Integer usageOrder; // 1st hint used, 2nd hint used, etc.
}