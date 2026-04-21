package com.banking.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Schema(description = "Amount to deposit", example = "500.00")
        BigDecimal amount
) {}
