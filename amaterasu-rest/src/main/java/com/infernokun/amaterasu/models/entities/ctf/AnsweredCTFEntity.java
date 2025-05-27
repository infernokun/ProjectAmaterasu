package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "answered_ctf_entity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ctf_entity_id"}))
public class AnsweredCTFEntity extends StoredObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctf_entity_id", nullable = false)
    private CTFEntity ctfEntity;

    @Builder.Default
    @Column(nullable = false)
    private Boolean correct = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    // Store all submitted answers
    @ElementCollection
    @CollectionTable(name = "ctf_answers", joinColumns = @JoinColumn(name = "answered_ctf_id"))
    @Column(name = "answer", length = 1000)
    @Builder.Default
    private List<String> answers = new ArrayList<>();

    // Store timestamps for each attempt
    @ElementCollection
    @CollectionTable(name = "ctf_attempt_times", joinColumns = @JoinColumn(name = "answered_ctf_id"))
    @Column(name = "attempt_time")
    @Builder.Default
    private List<LocalDateTime> times = new ArrayList<>();

    // When the challenge was solved (if correct)
    @Column(name = "solved_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime solvedAt;

    // Last attempt timestamp
    @Column(name = "last_attempt_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAttemptAt;

    @Builder.Default
    @Column(name = "score")
    private Integer score = 0;

    @Builder.Default
    @Column(name = "hints_used")
    private Integer hintsUsed = 0;

    // Optional: Time taken to solve (in seconds)
    @Column(name = "solve_time_seconds")
    private Long solveTimeSeconds;

    @PrePersist
    protected void onCreate() {
        if (this.getCreatedAt() == null) {
            this.setCreatedAt(LocalDateTime.now());
        }
        lastAttemptAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastAttemptAt = LocalDateTime.now();

        // Calculate solve time if solved
        if (correct && solvedAt != null && solveTimeSeconds == null) {
            solveTimeSeconds = Duration.between(this.getCreatedAt(), solvedAt).getSeconds();
        }
    }

    /**
     * Get the most recent answer
     */
    public String getLatestAnswer() {
        return answers != null && !answers.isEmpty() ?
                answers.getLast() : null;
    }

    /**
     * Get the most recent attempt time
     */
    public LocalDateTime getLatestAttemptTime() {
        return times != null && !times.isEmpty() ?
                times.getLast() : null;
    }

    /**
     * Check if max attempts reached
     */
    public boolean isMaxAttemptsReached() {
        return ctfEntity != null && attempts >= ctfEntity.getMaxAttempts();
    }

    /**
     * Get remaining attempts
     */
    public int getRemainingAttempts() {
        return ctfEntity != null ? Math.max(0, ctfEntity.getMaxAttempts() - attempts) : 0;
    }

    /**
     * Check if challenge is completed (correct or max attempts reached)
     */
    public boolean isCompleted() {
        return correct || isMaxAttemptsReached();
    }
}