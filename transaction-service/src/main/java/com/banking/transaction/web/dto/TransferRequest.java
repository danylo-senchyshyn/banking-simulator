package com.banking.transaction.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull
        @Schema(description = "Source account ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID fromAccountId,

        @NotNull
        @Schema(description = "Destination account ID", example = "4fb96g75-6828-5673-c4gd-3d074g77bgb7")
        UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Schema(description = "Transfer amount", example = "100.00")
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        @Schema(description = "Currency code", example = "USD")
        String currency
) {}
