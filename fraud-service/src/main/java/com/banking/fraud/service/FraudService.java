package com.banking.fraud.service;

import com.banking.fraud.domain.FraudAlert;
import com.banking.fraud.repository.FraudAlertRepository;
import com.banking.fraud.service.event.TransactionCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class FraudService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000");
    private static final int MAX_TRANSACTIONS_PER_HOUR = 10;
    private static final int MAX_SAME_PAIR_PER_DAY = 5;

    private final FraudAlertRepository fraudAlertRepository;
    private final Counter fraudRejectedCounter;

    public FraudService(FraudAlertRepository fraudAlertRepository, MeterRegistry meterRegistry) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudRejectedCounter = Counter.builder("fraud.rejected.count")
                .description("Total number of transactions rejected by fraud rules")
                .register(meterRegistry);
    }

    @Transactional
    public FraudResult evaluate(TransactionCreatedEvent event) {
        String reason = checkRules(event);
        boolean approved = reason == null;

        FraudAlert alert = FraudAlert.builder()
                .transactionId(event.transactionId())
                .senderId(event.senderId())
                .fromAccountId(event.fromAccountId())
                .toAccountId(event.toAccountId())
                .amount(event.amount())
                .currency(event.currency())
                .approved(approved)
                .reason(reason)
                .build();
        fraudAlertRepository.save(alert);

        if (!approved) {
            fraudRejectedCounter.increment();
        }
        log.info("Fraud evaluation: transactionId={}, approved={}, reason={}", event.transactionId(), approved, reason);
        return new FraudResult(approved, reason);
    }

    private String checkRules(TransactionCreatedEvent event) {
        if (event.fromAccountId().equals(event.toAccountId())) {
            return "Transfer to same account";
        }

        if (event.amount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            return "Amount exceeds limit of " + HIGH_AMOUNT_THRESHOLD;
        }

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long txLastHour = fraudAlertRepository.countRecentBySender(event.senderId(), oneHourAgo);
        if (txLastHour >= MAX_TRANSACTIONS_PER_HOUR) {
            return "Too many transactions in the last hour: " + (txLastHour + 1);
        }

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        long samePairLastDay = fraudAlertRepository.countRecentByAccountPair(
                event.fromAccountId(), event.toAccountId(), oneDayAgo);
        if (samePairLastDay >= MAX_SAME_PAIR_PER_DAY) {
            return "Too many transfers to the same account today: " + (samePairLastDay + 1);
        }

        return null;
    }

    public record FraudResult(boolean approved, String reason) {}
}
