package com.infernokun.amaterasu.models.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class ProxmoxVMListConverter extends JsonListConverter<ProxmoxVM> {
    @Override
    protected TypeReference<List<ProxmoxVM>> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected String getTypeName() {
        return "ProxmoxVM";
    }
}