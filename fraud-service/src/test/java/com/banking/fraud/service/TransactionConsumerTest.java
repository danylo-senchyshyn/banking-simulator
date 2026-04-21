package com.banking.fraud.service;

import com.banking.common.AppConstants;
import com.banking.fraud.service.event.FraudResultEvent;
import com.banking.fraud.service.event.TransactionCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    @Mock
    private FraudService fraudService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TransactionConsumer transactionConsumer;

    private TransactionCreatedEvent buildEvent() {
        return new TransactionCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.valueOf(200), "USD"
        );
    }

    @Test
    void consume_approved_publishesToFraudApprovedTopic() {
        var event = buildEvent();
        when(fraudService.evaluate(event)).thenReturn(new FraudService.FraudResult(true, null));

        transactionConsumer.consume(event);

        var captor = ArgumentCaptor.forClass(FraudResultEvent.class);
        verify(kafkaTemplate).send(
                eq(AppConstants.Kafka.FRAUD_APPROVED),
                eq(event.transactionId().toString()),
                captor.capture()
        );
        assertThat(captor.getValue().approved()).isTrue();
        assertThat(captor.getValue().transactionId()).isEqualTo(event.transactionId());
    }

    @Test
    void consume_rejected_publishesToFraudRejectedTopic() {
        var event = buildEvent();
        String reason = "Amount exceeds limit";
        when(fraudService.evaluate(event)).thenReturn(new FraudService.FraudResult(false, reason));

        transactionConsumer.consume(event);

        var captor = ArgumentCaptor.forClass(FraudResultEvent.class);
        verify(kafkaTemplate).send(
                eq(AppConstants.Kafka.FRAUD_REJECTED),
                eq(event.transactionId().toString()),
                captor.capture()
        );
        assertThat(captor.getValue().approved()).isFalse();
        assertThat(captor.getValue().reason()).isEqualTo(reason);
    }

    @Test
    void consume_alwaysCallsEvaluate() {
        var event = buildEvent();
        when(fraudService.evaluate(event)).thenReturn(new FraudService.FraudResult(true, null));

        transactionConsumer.consume(event);

        verify(fraudService).evaluate(event);
    }
}
