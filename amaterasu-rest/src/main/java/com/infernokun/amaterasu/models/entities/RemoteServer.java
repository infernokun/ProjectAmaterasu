package com.infernokun.amaterasu.models.entities;

import com.infernokun.amaterasu.models.enums.ServerType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remote_server")
@ToString(exclude = "remoteServerStats")
public class RemoteServer extends StoredObject {
    private String name;
    private String ipAddress;
    private String username;
    private String password;
    private String apiToken;
    private ServerType serverType;
    private String nodeName;
    @OneToOne(mappedBy = "remoteServer", cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "stats_id", referencedColumnName = "id")
    private RemoteServerStats remoteServerStats;
}
