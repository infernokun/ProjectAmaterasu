package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.util.List;

@Converter(autoApply = true)
public class LocalDateTimeListConverter extends JsonListConverter<LocalDateTime> {
    @Override
    protected TypeReference<List<LocalDateTime>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "LocalDataTime";
    }
}