package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.enums.DifficultyLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ctf_entity")
public class CTFEntity extends StoredObject {
    private String question;
    @Min(1)
    private Integer maxAttempts;
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;
    private String description;
    @OneToMany(mappedBy = "ctfEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<Hint> hints = new ArrayList<>();
    @OneToMany(mappedBy = "ctfEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<Flag> flags = new ArrayList<>();
    private String category;
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;
    @Min(0)
    private Integer points;
    private String author;
    private List<String> tags = new ArrayList<>();
    private Boolean visible;
    private Boolean isActive = true;
    private Integer solveCount = 0;
    private Integer attemptCount = 0;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime releaseDate = LocalDateTime.now();
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expirationDate;
    private List<String> attachments = new ArrayList<>();
    private String solutionExplanation;
    private List<String> relatedChallengeIds = new ArrayList<>();
}
