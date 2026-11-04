package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

@Data
@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 of the raw token
    @Column(length = 1000, nullable = false, unique = true)
    private String token;

    private Instant creationDate;
    private Instant expirationDate;

    @Column(name = "last_used")
    private Instant lastUsed;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 2000)
    private String userAgent;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "revoked")
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    // Raw token handed to the client once; only its hash is stored
    @Transient
    private String rawToken;

    public static final Duration IDLE_TIMEOUT = Duration.ofDays(7);
    public static final Duration MAX_LIFETIME = Duration.ofDays(30);

    /** Expired after a week unused, or a month after the session's first login, whichever is first. */
    public boolean isExpired() {
        Instant now = Instant.now();
        Instant lastActivity = lastUsed != null ? lastUsed : creationDate;
        return now.isAfter(expirationDate) || now.isAfter(lastActivity.plus(IDLE_TIMEOUT));
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public boolean isRevoked() {
        return Boolean.TRUE.equals(this.revoked);
    }
}
