package com.banking.transaction.service;

import com.banking.transaction.domain.MoneyAuditLog;
import com.banking.transaction.repository.MoneyAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MoneyAuditService {

    private final MoneyAuditLogRepository moneyAuditLogRepository;

    @Async
    public void logTransfer(UUID senderId, UUID transactionId, UUID fromAccountId,
                            UUID toAccountId, BigDecimal amount, String currency) {
        moneyAuditLogRepository.save(MoneyAuditLog.builder()
                .userId(senderId)
                .action("TRANSFER")
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency(currency)
                .build());
    }
}
