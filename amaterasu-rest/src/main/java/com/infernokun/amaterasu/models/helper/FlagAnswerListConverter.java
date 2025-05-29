package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.dto.ctf.FlagAnswerRequest;
import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class FlagAnswerListConverter extends JsonListConverter<FlagAnswerRequest> {
    @Override
    protected TypeReference<List<FlagAnswerRequest>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "FlagAnswer";
    }
}