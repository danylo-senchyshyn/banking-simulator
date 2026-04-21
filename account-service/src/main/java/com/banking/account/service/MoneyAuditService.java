package com.banking.account.service;

import com.banking.account.domain.MoneyAuditLog;
import com.banking.account.repository.MoneyAuditLogRepository;
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
    public void logDeposit(UUID userId, UUID accountId, BigDecimal amount, String currency) {
        moneyAuditLogRepository.save(MoneyAuditLog.builder()
                .userId(userId)
                .action("DEPOSIT")
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .build());
    }
}
