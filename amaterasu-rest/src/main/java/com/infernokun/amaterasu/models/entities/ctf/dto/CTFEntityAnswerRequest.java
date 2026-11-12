package com.infernokun.amaterasu.models.entities.ctf.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
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

    /** Set by the server when the attempt is stored; null on attempts from before it was kept. */
    @Transient
    private Boolean correct;

    /** Username of the admin or facilitator who gave credit for this attempt. Set by the server. */
    @Transient
    private String creditedBy;
}
