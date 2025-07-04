package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.helper.FlagAnswerListConverter;
import com.infernokun.amaterasu.models.helper.LocalDateTimeListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "answered_ctf_entity", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ctf_entity_id"}))
public class CTFEntityAnswer extends StoredObject {

    @ManyToOne
    @JoinColumn(name = "room_user_id", nullable = false)
    private RoomUser roomUser;

    @ManyToOne
    @JoinColumn(name = "ctf_entity_id", nullable = false)
    private CTFEntity ctfEntity;

    @Builder.Default
    @Column(nullable = false)
    private Boolean correct = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "answers", columnDefinition = "TEXT")
    @Convert(converter = FlagAnswerListConverter.class)
    @Builder.Default
    private List<CTFEntityAnswerRequest> answers = new ArrayList<>();

    @Column(name = "attempt_times", columnDefinition = "TEXT")
    @Convert(converter = LocalDateTimeListConverter.class)
    @Builder.Default
    private List<LocalDateTime> attemptTimes = new ArrayList<>();

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
    @OrderBy("usageOrder ASC")
    @OneToMany(mappedBy = "ctfEntityAnswer", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CTFEntityHintUsage> hintUsages = new ArrayList<>();

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

    public CTFEntityAnswerRequest getLatestAnswer() {
        return answers != null && !answers.isEmpty() ?
                answers.getLast() : null;
    }

    public LocalDateTime getLatestAttemptTime() {
        return attemptTimes != null && !attemptTimes.isEmpty() ?
                attemptTimes.getLast() : null;
    }

    public boolean isMaxAttemptsReached() {
        return ctfEntity != null && attempts >= ctfEntity.getMaxAttempts();
    }

    public int getRemainingAttempts() {
        return ctfEntity != null ? Math.max(0, ctfEntity.getMaxAttempts() - attempts) : 0;
    }

    public boolean isCompleted() {
        return correct || isMaxAttemptsReached();
    }

    public List<Hint> getHintsUsed() {
        return hintUsages.stream()
                .map(CTFEntityHintUsage::getHint)
                .collect(Collectors.toList());
    }

    public int getTotalPointsDeductedFromHints() {
        return hintUsages.stream()
                .mapToInt(CTFEntityHintUsage::getPointsDeducted)
                .sum();
    }

    public boolean hasUsedHint(Hint hint) {
        return hintUsages.stream()
                .anyMatch(usage -> usage.getHint().getId().equals(hint.getId()));
    }
}