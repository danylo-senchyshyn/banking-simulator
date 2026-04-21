package com.banking.fraud.service;

import com.banking.fraud.domain.FraudAlert;
import com.banking.fraud.repository.FraudAlertRepository;
import com.banking.fraud.service.event.TransactionCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudServiceTest {

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    private FraudService fraudService;

    private UUID senderId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        fraudService = new FraudService(fraudAlertRepository, new SimpleMeterRegistry());
        senderId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    private TransactionCreatedEvent event(UUID from, UUID to, BigDecimal amount) {
        return new TransactionCreatedEvent(transactionId, senderId, from, to, amount, "USD");
    }

    // --- Rule 1: same account ---

    @Test
    void evaluate_sameAccount_rejected() {
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, fromAccountId, BigDecimal.valueOf(100)));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("same account");
    }

    // --- Rule 2: high amount ---

    @Test
    void evaluate_amountExactlyAtThreshold_approved() {
        when(fraudAlertRepository.countRecentBySender(any(), any())).thenReturn(0L);
        when(fraudAlertRepository.countRecentByAccountPair(any(), any(), any())).thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, new BigDecimal("10000")));

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_amountExceedsThreshold_rejected() {
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, new BigDecimal("10000.01")));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("10000");
    }

    // --- Rule 3: velocity by sender ---

    @Test
    void evaluate_senderVelocityAtLimit_approved() {
        when(fraudAlertRepository.countRecentBySender(eq(senderId), any())).thenReturn(9L);
        when(fraudAlertRepository.countRecentByAccountPair(any(), any(), any())).thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(100)));

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_senderVelocityExceedsLimit_rejected() {
        when(fraudAlertRepository.countRecentBySender(eq(senderId), any())).thenReturn(10L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(100)));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("Too many transactions");
    }

    // --- Rule 4: same pair per day ---

    @Test
    void evaluate_samePairAtLimit_approved() {
        when(fraudAlertRepository.countRecentBySender(any(), any())).thenReturn(0L);
        when(fraudAlertRepository.countRecentByAccountPair(eq(fromAccountId), eq(toAccountId), any())).thenReturn(4L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(100)));

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_samePairExceedsLimit_rejected() {
        when(fraudAlertRepository.countRecentBySender(any(), any())).thenReturn(0L);
        when(fraudAlertRepository.countRecentByAccountPair(eq(fromAccountId), eq(toAccountId), any())).thenReturn(5L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(100)));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("Too many transfers");
    }

    // --- Alert is always saved ---

    @Test
    void evaluate_alwaysSavesFraudAlert() {
        when(fraudAlertRepository.countRecentBySender(any(), any())).thenReturn(0L);
        when(fraudAlertRepository.countRecentByAccountPair(any(), any(), any())).thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(50)));

        var captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo(transactionId);
        assertThat(captor.getValue().isApproved()).isTrue();
    }

    @Test
    void evaluate_rejectedAlert_savedWithReason() {
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        fraudService.evaluate(event(fromAccountId, toAccountId, new BigDecimal("99999")));

        var captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().isApproved()).isFalse();
        assertThat(captor.getValue().getReason()).isNotBlank();
    }

    // --- Rule priority: same account checked first ---

    @Test
    void evaluate_sameAccountSkipsOtherChecks() {
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        fraudService.evaluate(event(fromAccountId, fromAccountId, new BigDecimal("99999")));

        verify(fraudAlertRepository, never()).countRecentBySender(any(), any());
        verify(fraudAlertRepository, never()).countRecentByAccountPair(any(), any(), any());
    }

    // --- Happy path ---

    @Test
    void evaluate_validTransaction_approved() {
        when(fraudAlertRepository.countRecentBySender(any(), any())).thenReturn(0L);
        when(fraudAlertRepository.countRecentByAccountPair(any(), any(), any())).thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = fraudService.evaluate(event(fromAccountId, toAccountId, BigDecimal.valueOf(500)));

        assertThat(result.approved()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
