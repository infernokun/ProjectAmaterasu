package com.infernokun.amaterasu.models.dto;

import com.infernokun.amaterasu.models.enums.ServerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteServerRequest {
    private String name;
    private String ipAddress;
    private int port;
    private String username;
    private String password;
    private String apiToken;
    private ServerType serverType;
    private String nodeName;
}
