package com.banking.account.service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceUpdateEvent(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {}
