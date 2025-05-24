package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ctf_entity")
public class CTFEntity extends StoredObject {
    private String question;
    private Integer maxAttempts;
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;
    private String description;
    private List<String> hints;
    @OneToMany(mappedBy = "ctfEntity", cascade = CascadeType.ALL)
    @JsonManagedReference
    @JsonIgnore
    private List<Flag> flags;
    private String category;
    private String difficultyLevel;
    private Integer points;
    private String author;
    private List<String> tags;
    private Boolean visible;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime releaseDate = LocalDateTime.now();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expirationDate;
    private List<String> attachments;
    private String solutionExplanation;
    private List<String> relatedChallengeIds;
}
