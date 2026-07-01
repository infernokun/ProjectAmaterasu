package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.proxmox.LabNetworkConfig;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class LabNetworkConfigListConverter extends JsonListConverter<LabNetworkConfig> {
    @Override
    protected TypeReference<List<LabNetworkConfig>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "LabNetworkConfig";
    }
}
