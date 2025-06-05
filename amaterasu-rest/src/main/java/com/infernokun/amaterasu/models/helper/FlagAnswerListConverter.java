package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class FlagAnswerListConverter extends JsonListConverter<CTFEntityAnswerRequest> {
    @Override
    protected TypeReference<List<CTFEntityAnswerRequest>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "FlagAnswer";
    }
}