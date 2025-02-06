package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remote_server")
public class RemoteServer extends StoredObject {
    private String name;
    private String ipAddress;
    @OneToOne
    @JoinColumn(name = "stats_id", referencedColumnName = "id")
    private RemoteServerStats remoteServerStats;
}
