package com.banking.transaction.service;

import com.banking.transaction.domain.Transaction;
import com.banking.transaction.domain.TransactionStatus;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.repository.OutboxEventRepository;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.web.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private MoneyAuditService moneyAuditService;

    private TransactionService transactionService;

    private UUID senderId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, outboxEventRepository, objectMapper, moneyAuditService, new SimpleMeterRegistry());
        senderId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();
    }

    private TransferRequest request(UUID from, UUID to) {
        return new TransferRequest(from, to, BigDecimal.valueOf(200), "USD");
    }

    private Transaction buildTransaction(UUID id) {
        Transaction t = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .senderId(senderId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(BigDecimal.valueOf(200))
                .currency("USD")
                .build();
        t.setId(id);
        return t;
    }

    // ===== transfer =====

    @Test
    void transfer_newRequest_savesPendingAndQueuesOutbox() throws Exception {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        Transaction saved = buildTransaction(UUID.randomUUID());
        when(transactionRepository.save(any())).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Transaction result = transactionService.transfer(senderId, idempotencyKey, request(fromAccountId, toAccountId));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void transfer_duplicateIdempotencyKey_throwsConflict() {
        Transaction existing = buildTransaction(UUID.randomUUID());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionService.transfer(senderId, idempotencyKey, request(fromAccountId, toAccountId)))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_sameFromAndTo_throwsBadRequest() {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(senderId, idempotencyKey, request(fromAccountId, fromAccountId)))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void transfer_savesOutboxWithCorrectTopic() throws Exception {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        Transaction saved = buildTransaction(UUID.randomUUID());
        when(transactionRepository.save(any())).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        transactionService.transfer(senderId, idempotencyKey, request(fromAccountId, toAccountId));

        verify(outboxEventRepository).save(argThat(e -> "transaction.created".equals(e.getTopic())));
    }

    // ===== getTransaction =====

    @Test
    void getTransaction_ownTransaction_returnsIt() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        Transaction result = transactionService.getTransaction(txId, senderId);

        assertThat(result).isEqualTo(tx);
    }

    @Test
    void getTransaction_notFound_throwsNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(txId, senderId))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getTransaction_differentOwner_throwsNotFound() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.getTransaction(txId, UUID.randomUUID()))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ===== getMyTransactions =====

    @Test
    void getMyTransactions_returnsPaginatedResults() {
        Transaction tx = buildTransaction(UUID.randomUUID());
        Page<Transaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findAllBySenderId(eq(senderId), any(Pageable.class))).thenReturn(page);

        Page<Transaction> result = transactionService.getMyTransactions(senderId, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ===== markCompleted =====

    @Test
    void markCompleted_existingTransaction_setsStatusCompleted() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        transactionService.markCompleted(txId);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(transactionRepository).save(tx);
    }

    @Test
    void markCompleted_notFound_throwsNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.markCompleted(txId))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ===== markBlocked =====

    @Test
    void markBlocked_existingTransaction_setsStatusAndReason() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        transactionService.markBlocked(txId, "Too many transactions");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.BLOCKED);
        assertThat(tx.getFailureReason()).isEqualTo("Too many transactions");
    }

    @Test
    void markBlocked_notFound_throwsNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.markBlocked(txId, "reason"))
                .isInstanceOf(TransactionException.class)
                .satisfies(e -> assertThat(((TransactionException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
