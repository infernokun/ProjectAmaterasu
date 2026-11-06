package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

/** Something one user should know about, such as their team's lab failing or points awarded to them. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipientId,createdAt")
})
public class Notification {
    @Id
    @UuidGenerator
    private String id;

    @JsonIgnore
    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    /** A path in the web app, such as /labs/{trackerId}. */
    private String link;

    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant readAt;
}
