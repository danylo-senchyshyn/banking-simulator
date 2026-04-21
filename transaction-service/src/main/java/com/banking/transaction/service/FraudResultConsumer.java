package com.banking.transaction.service;

import com.banking.common.AppConstants;
import com.banking.transaction.domain.Transaction;
import com.banking.transaction.service.event.BalanceUpdateEvent;
import com.banking.transaction.service.event.FraudResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudResultConsumer {

    private final TransactionService transactionService;

    @Transactional
    @KafkaListener(
            topics = {AppConstants.Kafka.FRAUD_APPROVED, AppConstants.Kafka.FRAUD_REJECTED},
            groupId = "transaction-service"
    )
    public void consume(FraudResultEvent event) {
        log.info("Fraud result: transactionId={}, approved={}", event.transactionId(), event.approved());

        if (event.approved()) {
            transactionService.markCompleted(event.transactionId());
            Transaction tx = transactionService.getById(event.transactionId());
            transactionService.saveOutboxEvent(
                    AppConstants.Kafka.BALANCE_UPDATE,
                    tx.getId().toString(),
                    new BalanceUpdateEvent(tx.getId(), tx.getFromAccountId(), tx.getToAccountId(),
                            tx.getAmount(), tx.getCurrency())
            );
        } else {
            transactionService.markBlocked(event.transactionId(), event.reason());
        }
    }
}
