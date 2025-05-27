package com.infernokun.amaterasu.models.dto.ctf;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalDateTime;

@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FlagAnswer {
    @Column(name = "flag", length = 1000)
    private String flag;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "question_id")
    private String questionId;

    @Column(name = "submission_time")
    @Builder.Default
    private LocalDateTime submissionTime = LocalDateTime.now();
}
