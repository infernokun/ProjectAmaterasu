package com.infernokun.amaterasu.models;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RemoteCommandResponse {
    private String output;
    private String error;
    private String both;

    public RemoteCommandResponse(String output, String error) {
        this.output = output;
        this.error = error;
        this.both = this.output + this.error;
    }
}
