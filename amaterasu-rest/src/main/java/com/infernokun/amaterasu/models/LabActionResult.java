package com.infernokun.amaterasu.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabActionResult {
    // Without this, Lombok's isSuccessful() getter makes Jackson emit "successful",
    // but the Angular LabActionResult model reads "isSuccessful". Pin the JSON name.
    @JsonProperty("isSuccessful")
    private boolean isSuccessful;
    private LabTracker labTracker;
    private String output;
}
