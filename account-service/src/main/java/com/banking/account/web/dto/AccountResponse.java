package com.banking.account.web.dto;

import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Account details")
public record AccountResponse(
        @Schema(description = "Account ID")
        UUID id,

        @Schema(description = "Owner user ID")
        UUID userId,

        @Schema(description = "Currency", example = "USD")
        Currency currency,

        @Schema(description = "Account status", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "Current balance", example = "1500.00")
        BigDecimal balance,

        @Schema(description = "Created at")
        LocalDateTime createdAt
) {}
