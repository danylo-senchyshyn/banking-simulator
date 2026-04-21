package com.banking.account.web.dto;

import com.banking.account.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to create a new account")
public record CreateAccountRequest(
        @NotNull
        @Schema(description = "Account currency", example = "USD")
        Currency currency
) {}
