package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class DockerServiceInfoConverter implements AttributeConverter<List<DockerServiceInfo>, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DockerServiceInfo> dockerServiceInfoList) {
        try {
            return objectMapper.writeValueAsString(dockerServiceInfoList);
        } catch (Exception e) {
            throw new RuntimeException("Error converting DockerServiceInfo list to JSON", e);
        }
    }

    @Override
    public List<DockerServiceInfo> convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to DockerServiceInfo list", e);
        }
    }
}
