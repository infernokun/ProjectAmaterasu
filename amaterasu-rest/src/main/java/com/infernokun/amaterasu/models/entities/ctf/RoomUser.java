package com.infernokun.amaterasu.models.entities.ctf;

import com.infernokun.amaterasu.models.entities.StoredObject;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUser extends StoredObject {
    @ManyToOne
    private Room room;
    @ManyToOne
    private User user;
    @Builder.Default
    private Integer points = 0;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoomUserStatus roomUserStatus = RoomUserStatus.NONE;
}
