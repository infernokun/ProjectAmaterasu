package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;

import java.util.ArrayList;
import java.util.List;

public abstract class JsonListConverter<T> implements AttributeConverter<List<T>, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    protected abstract TypeReference<List<T>> getTypeReference();
    protected abstract String getTypeName();

    @Override
    public String convertToDatabaseColumn(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting list of " + getTypeName() + " to JSON", e);
        }
    }

    @Override
    public List<T> convertToEntityAttribute(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, getTypeReference());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting JSON to list of " + getTypeName(), e);
        }
    }
}