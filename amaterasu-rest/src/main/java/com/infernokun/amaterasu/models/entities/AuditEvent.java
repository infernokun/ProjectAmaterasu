package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/** Something a person or the system did: a lab action, a sign in, an admin change. Never edited. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_at", columnList = "at"),
        @Index(name = "idx_audit_team", columnList = "teamId"),
        @Index(name = "idx_audit_target", columnList = "targetType,targetId")
})
public class AuditEvent {
    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private Instant at;

    private String actorId;
    private String actorUsername;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 32)
    private String targetType;
    private String targetId;
    private String targetLabel;

    private String teamId;

    @Column(length = 1000)
    private String detail;
}
