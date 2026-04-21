package com.banking.transaction.service;

import com.banking.common.AppConstants;
import com.banking.transaction.domain.OutboxEvent;
import com.banking.transaction.domain.Transaction;
import com.banking.transaction.domain.TransactionStatus;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.repository.OutboxEventRepository;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.event.TransactionCreatedEvent;
import com.banking.transaction.web.dto.TransferRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MoneyAuditService moneyAuditService;
    private final Counter transactionCreatedCounter;
    private final Counter transactionCompletedCounter;
    private final Counter transactionBlockedCounter;

    public TransactionService(TransactionRepository transactionRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper,
                              MoneyAuditService moneyAuditService,
                              MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.moneyAuditService = moneyAuditService;
        this.transactionCreatedCounter = Counter.builder("transaction.created.count")
                .description("Total number of transfer transactions created")
                .register(meterRegistry);
        this.transactionCompletedCounter = Counter.builder("transaction.completed.count")
                .description("Total number of transactions completed")
                .register(meterRegistry);
        this.transactionBlockedCounter = Counter.builder("transaction.blocked.count")
                .description("Total number of transactions blocked by fraud")
                .register(meterRegistry);
    }

    @Transactional
    public Transaction transfer(UUID senderId, String idempotencyKey, TransferRequest request) {
        log.info("Transfer: senderId={}, from={}, to={}, amount={} {}",
                senderId, request.fromAccountId(), request.toAccountId(), request.amount(), request.currency());
        transactionRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            throw TransactionException.conflict("Duplicate request: idempotency key already used");
        });

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw TransactionException.badRequest("Source and destination accounts must differ");
        }

        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .senderId(senderId)
                .fromAccountId(request.fromAccountId())
                .toAccountId(request.toAccountId())
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .build();

        transaction = transactionRepository.save(transaction);
        transactionCreatedCounter.increment();
        log.info("Transaction saved as PENDING: id={}", transaction.getId());
        moneyAuditService.logTransfer(senderId, transaction.getId(),
                request.fromAccountId(), request.toAccountId(), request.amount(), request.currency().toUpperCase());

        TransactionCreatedEvent event = new TransactionCreatedEvent(
                transaction.getId(),
                transaction.getSenderId(),
                transaction.getFromAccountId(),
                transaction.getToAccountId(),
                transaction.getAmount(),
                transaction.getCurrency()
        );
        saveOutboxEvent(AppConstants.Kafka.TRANSACTION_CREATED, transaction.getId().toString(), event);

        return transaction;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getMyTransactions(UUID senderId, Pageable pageable) {
        return transactionRepository.findAllBySenderId(senderId, pageable);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID transactionId, UUID senderId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> TransactionException.notFound("Transaction not found"));
        if (!transaction.getSenderId().equals(senderId)) {
            throw TransactionException.notFound("Transaction not found");
        }
        return transaction;
    }

    @Transactional
    public void markCompleted(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> TransactionException.notFound("Transaction not found: " + transactionId));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);
        transactionCompletedCounter.increment();
        log.info("Transaction {} marked COMPLETED", transactionId);
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> TransactionException.notFound("Transaction not found: " + transactionId));
    }

    @Transactional
    public void markBlocked(UUID transactionId, String reason) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> TransactionException.notFound("Transaction not found: " + transactionId));
        transaction.setStatus(TransactionStatus.BLOCKED);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
        transactionBlockedCounter.increment();
        log.info("Transaction {} marked BLOCKED, reason={}", transactionId, reason);
    }

    public void saveOutboxEvent(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .topic(topic)
                    .key(key)
                    .payload(json)
                    .sent(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox: saved event topic={}, key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload for topic=" + topic, e);
        }
    }
}
