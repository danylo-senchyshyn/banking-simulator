package com.banking.transaction.integration;

import com.banking.common.AppConstants;
import com.banking.transaction.domain.OutboxEvent;
import com.banking.transaction.domain.Transaction;
import com.banking.transaction.domain.TransactionStatus;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.repository.OutboxEventRepository;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.FraudResultConsumer;
import com.banking.transaction.service.OutboxPublisher;
import com.banking.transaction.service.TransactionService;
import com.banking.transaction.web.dto.TransferRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TransactionServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transaction_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    TransactionService transactionService;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    // Mock Kafka-dependent beans to keep tests focused on the DB layer
    @MockBean
    OutboxPublisher outboxPublisher;

    @MockBean
    FraudResultConsumer fraudResultConsumer;

    @AfterEach
    void cleanup() {
        outboxEventRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    // ===== transfer =====

    @Test
    void transfer_persistsTransactionAndOutboxEvent() {
        UUID senderId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, BigDecimal.valueOf(150.00), "USD");

        transactionService.transfer(senderId, idempotencyKey, request);

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        Transaction saved = transactions.get(0);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(saved.getSenderId()).isEqualTo(senderId);
        assertThat(saved.getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(saved.getToAccountId()).isEqualTo(toAccountId);

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        OutboxEvent outbox = outboxEvents.get(0);
        assertThat(outbox.isSent()).isFalse();
        assertThat(outbox.getTopic()).isEqualTo(AppConstants.Kafka.TRANSACTION_CREATED);
        assertThat(outbox.getKey()).isEqualTo(saved.getId().toString());
    }

    @Test
    void transfer_duplicateIdempotencyKey_throwsConflict() {
        UUID senderId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, BigDecimal.valueOf(100.00), "EUR");

        transactionService.transfer(senderId, idempotencyKey, request);

        assertThatThrownBy(() -> transactionService.transfer(senderId, idempotencyKey, request))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void transfer_sameAccounts_throwsBadRequest() {
        UUID senderId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(accountId, accountId, BigDecimal.valueOf(50.00), "USD");

        assertThatThrownBy(() -> transactionService.transfer(senderId, idempotencyKey, request))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    // ===== markCompleted =====

    @Test
    void markCompleted_updatesStatusInDatabase() {
        Transaction pending = transactionRepository.save(buildPendingTransaction());

        transactionService.markCompleted(pending.getId());

        Transaction updated = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    // ===== markBlocked =====

    @Test
    void markBlocked_updatesStatusAndReasonInDatabase() {
        Transaction pending = transactionRepository.save(buildPendingTransaction());
        String reason = "Suspicious activity detected";

        transactionService.markBlocked(pending.getId(), reason);

        Transaction updated = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.BLOCKED);
        assertThat(updated.getFailureReason()).isEqualTo(reason);
    }

    // ===== saveOutboxEvent =====

    @Test
    void saveOutboxEvent_persistsWithSentFalse() {
        String topic = AppConstants.Kafka.TRANSACTION_CREATED;
        String key = UUID.randomUUID().toString();
        Object payload = java.util.Map.of("transactionId", key);

        transactionService.saveOutboxEvent(topic, key, payload);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.isSent()).isFalse();
        assertThat(event.getTopic()).isEqualTo(topic);
        assertThat(event.getKey()).isEqualTo(key);
        assertThat(event.getPayload()).contains(key);
        assertThat(event.getSentAt()).isNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }

    // ===== getMyTransactions =====

    @Test
    void getMyTransactions_returnsOnlyOwnTransactions() {
        UUID senderA = UUID.randomUUID();
        UUID senderB = UUID.randomUUID();

        Transaction txA = buildPendingTransactionForSender(senderA);
        Transaction txB = buildPendingTransactionForSender(senderB);
        transactionRepository.saveAll(List.of(txA, txB));

        Page<Transaction> resultA = transactionService.getMyTransactions(senderA, Pageable.unpaged());
        Page<Transaction> resultB = transactionService.getMyTransactions(senderB, Pageable.unpaged());

        assertThat(resultA.getTotalElements()).isEqualTo(1);
        assertThat(resultA.getContent().get(0).getSenderId()).isEqualTo(senderA);

        assertThat(resultB.getTotalElements()).isEqualTo(1);
        assertThat(resultB.getContent().get(0).getSenderId()).isEqualTo(senderB);
    }

    // ===== helpers =====

    private Transaction buildPendingTransaction() {
        return buildPendingTransactionForSender(UUID.randomUUID());
    }

    private Transaction buildPendingTransactionForSender(UUID senderId) {
        return Transaction.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .senderId(senderId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(200.00))
                .currency("USD")
                .build();
    }
}
