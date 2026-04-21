package com.banking.transaction.service.event;

import java.util.UUID;

public record FraudResultEvent(
        UUID transactionId,
        boolean approved,
        String reason
) {}
