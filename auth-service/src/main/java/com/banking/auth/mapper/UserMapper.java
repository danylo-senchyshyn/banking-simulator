package com.banking.auth.mapper;

import com.banking.auth.domain.User;
import com.banking.auth.web.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
