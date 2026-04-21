package com.banking.transaction.web.dto;

import com.banking.transaction.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(

        @Schema(description = "Transaction ID")
        UUID id,

        @Schema(description = "Idempotency key")
        String idempotencyKey,

        @Schema(description = "Sender user ID")
        UUID senderId,

        @Schema(description = "Source account ID")
        UUID fromAccountId,

        @Schema(description = "Destination account ID")
        UUID toAccountId,

        @Schema(description = "Transfer amount")
        BigDecimal amount,

        @Schema(description = "Currency code")
        String currency,

        @Schema(description = "Transaction status")
        TransactionStatus status,

        @Schema(description = "Failure reason if blocked")
        String failureReason,

        @Schema(description = "Created at")
        LocalDateTime createdAt,

        @Schema(description = "Last updated at")
        LocalDateTime updatedAt
) {}
