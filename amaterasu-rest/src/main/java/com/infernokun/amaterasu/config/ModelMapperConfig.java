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

        // Skips are set before implicit matching, since labId and roomId would both match setId
        mapper.getConfiguration().setImplicitMappingEnabled(false);
        mapper.createTypeMap(CTFEntityRequest.class, CTFEntity.class)
                .addMappings(mapping -> {
                    mapping.skip(CTFEntity::setId);
                    mapping.map(CTFEntityRequest::getLabId, CTFEntity::setLabId);
                    mapping.skip(CTFEntity::setCreatedAt);
                    mapping.skip(CTFEntity::setUpdatedAt);
                    // Parsed by the controller, which accepts more than one date format
                    mapping.skip(CTFEntity::setReleaseDate);
                    mapping.skip(CTFEntity::setExpirationDate);
                })
                .implicitMappings();
        mapper.getConfiguration().setImplicitMappingEnabled(true);

        mapper.createTypeMap(FlagRequest.class, Flag.class)
                .addMappings(mapping -> {
                    mapping.skip(Flag::setId);
                    mapping.skip(Flag::setCtfEntity); // We'll set this manually
                });
        return mapper;
    }
}
