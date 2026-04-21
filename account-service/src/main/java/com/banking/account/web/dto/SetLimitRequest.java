package com.banking.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request to update card transaction limit")
public record SetLimitRequest(
        @NotNull
        @DecimalMin("0.01")
        @Schema(description = "New max amount per single transaction", example = "500.00")
        BigDecimal transactionLimit
) {}
