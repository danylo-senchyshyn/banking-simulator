package com.banking.auth.web.dto;

import com.banking.auth.domain.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        boolean blocked,
        LocalDateTime createdAt
) {}
