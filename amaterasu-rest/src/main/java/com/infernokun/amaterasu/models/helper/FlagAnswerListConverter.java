package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFAnswerRequest;
import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class FlagAnswerListConverter extends JsonListConverter<CTFAnswerRequest> {
    @Override
    protected TypeReference<List<CTFAnswerRequest>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "FlagAnswer";
    }
}