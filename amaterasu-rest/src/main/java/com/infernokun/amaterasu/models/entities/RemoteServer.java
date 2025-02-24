package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.enums.ServerType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remote_server")
public class RemoteServer extends StoredObject {
    private String name;
    private String ipAddress;
    private String username;
    private String password;
    private String apiToken;
    private ServerType serverType;
    private String nodeName;
    @OneToOne
    @JoinColumn(name = "stats_id", referencedColumnName = "id")
    private RemoteServerStats remoteServerStats;
}
