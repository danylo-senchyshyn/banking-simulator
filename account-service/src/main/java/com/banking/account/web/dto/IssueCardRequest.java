package com.banking.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

@Schema(description = "Request to issue a new card")
public record IssueCardRequest(
        @DecimalMin("0.01")
        @Schema(description = "Max amount per single transaction; null means no limit", example = "1000.00")
        BigDecimal transactionLimit
) {}
