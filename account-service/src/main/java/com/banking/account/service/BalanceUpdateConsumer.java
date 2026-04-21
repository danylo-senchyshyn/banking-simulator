package com.banking.account.service;

import com.banking.account.service.event.BalanceUpdateEvent;
import com.banking.common.AppConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceUpdateConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = AppConstants.Kafka.BALANCE_UPDATE, groupId = "account-service")
    @CircuitBreaker(name = "balance-update", fallbackMethod = "consumeFallback")
    public void consume(BalanceUpdateEvent event) {
        log.info("Balance update: transactionId={}, from={}, to={}, amount={}",
                event.transactionId(), event.fromAccountId(), event.toAccountId(), event.amount());
        accountService.applyBalanceUpdate(event.fromAccountId(), event.toAccountId(), event.amount());
    }

    void consumeFallback(BalanceUpdateEvent event, Throwable t) {
        log.error("Balance update CB open: transactionId={}, reason={}", event.transactionId(), t.getMessage());
        throw new RuntimeException("Balance update circuit open, will retry via DLQ", t);
    }
}
