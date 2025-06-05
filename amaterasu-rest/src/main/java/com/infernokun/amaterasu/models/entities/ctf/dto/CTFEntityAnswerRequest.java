package com.infernokun.amaterasu.models.entities.ctf.dto;

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
public class CTFEntityAnswerRequest {
    @Column(name = "flag", length = 1000)
    private String flag;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "room_id")
    private String roomId;

    @Column(name = "question_id")
    private String questionId;

    @Column(name = "submission_time")
    @Builder.Default
    private LocalDateTime submissionTime = LocalDateTime.now();
}
