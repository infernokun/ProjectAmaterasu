package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class DockerServiceInfoConverter extends JsonListConverter<DockerServiceInfo> {
    @Override
    protected TypeReference<List<DockerServiceInfo>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "DockerServiceInfo";
    }
}