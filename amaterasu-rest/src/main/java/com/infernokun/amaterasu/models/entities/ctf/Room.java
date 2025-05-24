package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.utils.AmaterasuConstants;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room extends StoredObject {
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creators")
    private User creator;
    @ElementCollection
    @JsonIgnore
    @Builder.Default
    private List<String> facilitators = new ArrayList<>();
    @Builder.Default
    private String surroundTag = AmaterasuConstants.DEFAULT_SURROUND_TAG;
}
