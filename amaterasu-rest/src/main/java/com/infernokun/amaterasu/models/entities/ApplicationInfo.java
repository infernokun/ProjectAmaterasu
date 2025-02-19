package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "application_info")
public class ApplicationInfo extends StoredObject {
    private String name = "Project Amaterasu";
    private String description = "A lab orchestrator.";
    @Column(columnDefinition = "TEXT")
    private String settings = "{}";

    public void setSettings(Object settingsObj) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Serialize the object to JSON string
            this.settings = objectMapper.writeValueAsString(settingsObj);
        } catch (JsonProcessingException e) {
            this.settings = "{}"; // Fallback in case of error
        }
    }

    public <T> T getSettings(Class<T> clazz) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Deserialize the JSON string back to the given class
            return objectMapper.readValue(this.settings, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
