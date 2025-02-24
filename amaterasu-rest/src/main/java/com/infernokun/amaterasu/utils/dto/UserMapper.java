package com.infernokun.amaterasu.utils.dto;

import com.infernokun.amaterasu.models.dto.UserDTO;
import com.infernokun.amaterasu.models.entities.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toDTO(User user);
}