package com.banking.fraud.service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionCreatedEvent(
        UUID transactionId,
        UUID senderId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {}
