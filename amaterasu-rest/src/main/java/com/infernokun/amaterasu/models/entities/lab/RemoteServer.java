package com.infernokun.amaterasu.models.entities.lab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.infernokun.amaterasu.models.entities.StoredObject;
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
    private Integer port;
    @JsonIgnore
    private String username;
    @JsonIgnore
    private String password;
    @JsonIgnore
    private String apiToken;
    private ServerType serverType;
    private String nodeName;
    @OneToOne(mappedBy = "remoteServer", cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "stats_id", referencedColumnName = "id")
    private RemoteServerStats remoteServerStats;
}
