package com.banking.auth.web.dto;

import com.banking.auth.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
