package com.banking.account.service;

import com.banking.account.service.event.BalanceUpdateEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BalanceUpdateConsumerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private BalanceUpdateConsumer consumer;

    @Test
    void consume_callsApplyBalanceUpdateWithCorrectArgs() {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(300);

        consumer.consume(new BalanceUpdateEvent(txId, fromId, toId, amount, "USD"));

        verify(accountService).applyBalanceUpdate(fromId, toId, amount);
    }

    @Test
    void consumeFallback_throwsRuntimeException() {
        UUID txId = UUID.randomUUID();
        BalanceUpdateEvent event = new BalanceUpdateEvent(txId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "USD");
        RuntimeException cause = new RuntimeException("DB down");

        assertThatThrownBy(() -> consumer.consumeFallback(event, cause))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circuit open");
    }
}
