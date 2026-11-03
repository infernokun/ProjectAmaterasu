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
    // Keep the JSON name "isSuccessful" to match the web model.
    @JsonProperty("isSuccessful")
    private boolean isSuccessful;
    private LabTracker labTracker;
    private String output;
}
