package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Converter(autoApply = true)
public class ProxmoxVMListConverter implements AttributeConverter<List<ProxmoxVM>, String> {
    private final Logger LOGGER = LoggerFactory.getLogger(ProxmoxVMListConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ProxmoxVM> proxmoxVMList) {
        try {
            return objectMapper.writeValueAsString(proxmoxVMList);
        } catch (IOException e) {
            throw new RuntimeException("Error converting list of ProxmoxVM to JSON", e);
        }
    }

    @Override
    public List<ProxmoxVM> convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProxmoxVM>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Error converting JSON to list of ProxmoxVM", e);
        }
    }
}