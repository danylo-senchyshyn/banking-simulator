package com.banking.transaction.service;

import com.banking.transaction.domain.Transaction;
import com.banking.transaction.service.event.FraudResultEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudResultConsumerTest {

    @Mock private TransactionService transactionService;

    @InjectMocks
    private FraudResultConsumer consumer;

    private Transaction buildTransaction(UUID txId) {
        Transaction tx = Transaction.builder()
                .idempotencyKey("key")
                .senderId(UUID.randomUUID())
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(150))
                .currency("USD")
                .build();
        tx.setId(txId);
        return tx;
    }

    @Test
    void consume_approved_marksCompletedAndQueuesOutbox() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        when(transactionService.getById(txId)).thenReturn(tx);

        consumer.consume(new FraudResultEvent(txId, true, null));

        verify(transactionService).markCompleted(txId);
        verify(transactionService).saveOutboxEvent(anyString(), anyString(), any());
    }

    @Test
    void consume_rejected_marksBlockedWithReason() {
        UUID txId = UUID.randomUUID();
        String reason = "Amount exceeds limit";

        consumer.consume(new FraudResultEvent(txId, false, reason));

        verify(transactionService).markBlocked(txId, reason);
        verify(transactionService, never()).markCompleted(any());
        verify(transactionService, never()).saveOutboxEvent(any(), any(), any());
    }

    @Test
    void consume_approved_doesNotCallMarkBlocked() {
        UUID txId = UUID.randomUUID();
        when(transactionService.getById(txId)).thenReturn(buildTransaction(txId));

        consumer.consume(new FraudResultEvent(txId, true, null));

        verify(transactionService, never()).markBlocked(any(), any());
    }
}
