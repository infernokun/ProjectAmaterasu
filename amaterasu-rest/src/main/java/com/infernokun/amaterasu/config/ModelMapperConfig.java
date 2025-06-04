package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.FlagRequest;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();

        mapper.createTypeMap(CTFEntityRequest.class, CTFEntity.class)
                .addMappings(mapping -> {
                    mapping.skip(CTFEntity::setId);
                    mapping.skip(CTFEntity::setCreatedAt);
                    mapping.skip(CTFEntity::setUpdatedAt);
                });

        mapper.createTypeMap(FlagRequest.class, Flag.class)
                .addMappings(mapping -> {
                    mapping.skip(Flag::setId);
                    mapping.skip(Flag::setCtfEntity); // We'll set this manually
                });
        return mapper;
    }
}
