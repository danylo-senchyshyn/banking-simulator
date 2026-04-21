package com.banking.transaction.service;

import com.banking.transaction.domain.OutboxEvent;
import com.banking.transaction.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        lenient().when(outboxEventRepository.countBySentFalse()).thenReturn(0L);
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, objectMapper, new SimpleMeterRegistry());
    }

    private OutboxEvent buildEvent(String topic) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setTopic(topic);
        event.setKey(UUID.randomUUID().toString());
        event.setPayload("{\"id\":\"test\"}");
        event.setSent(false);
        return event;
    }

    @Test
    void publishPending_marksEventAsSent() throws Exception {
        OutboxEvent event = buildEvent("transaction.created");
        when(outboxEventRepository.findTop50BySentFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(new Object());

        outboxPublisher.publishPending();

        verify(kafkaTemplate).send(eq("transaction.created"), anyString(), any());
        assertThat(event.isSent()).isTrue();
    }

    @Test
    void publishPending_kafkaFailure_doesNotMarkSent() throws Exception {
        OutboxEvent event = buildEvent("transaction.created");
        when(outboxEventRepository.findTop50BySentFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(new Object());
        doThrow(new RuntimeException("Kafka unavailable")).when(kafkaTemplate).send(anyString(), anyString(), any());

        outboxPublisher.publishPending();

        assertThat(event.isSent()).isFalse();
    }

    @Test
    void publishPending_noEvents_doesNothing() {
        when(outboxEventRepository.findTop50BySentFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        outboxPublisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
