package com.banking.account.web.dto;

import com.banking.account.domain.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Card details")
public record CardResponse(
        @Schema(description = "Card ID") UUID id,
        @Schema(description = "Associated account ID") UUID accountId,
        @Schema(description = "Masked card number", example = "4111 **** **** 1234") String cardNumber,
        @Schema(description = "Card status", example = "ACTIVE") CardStatus status,
        @Schema(description = "Max amount per single transaction; null = no limit", example = "1000.00") BigDecimal transactionLimit,
        @Schema(description = "Issued at") LocalDateTime createdAt
) {}
