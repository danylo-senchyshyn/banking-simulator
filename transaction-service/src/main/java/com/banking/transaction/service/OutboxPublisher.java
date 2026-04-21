package com.banking.transaction.service;

import com.banking.transaction.domain.OutboxEvent;
import com.banking.transaction.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        Gauge.builder("outbox.pending.size", outboxEventRepository, r -> r.countBySentFalse())
                .description("Number of unsent outbox events")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = outboxEventRepository.findTop50BySentFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.warn("Outbox: skipping event topic={}, key={}", event.getTopic(), event.getKey());
            }
        }
    }

    @CircuitBreaker(name = "kafka-outbox", fallbackMethod = "publishFallback")
    void publishEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getKey(),
                    objectMapper.readValue(event.getPayload(), Object.class));
            event.setSent(true);
            event.setSentAt(LocalDateTime.now());
            log.info("Outbox: published topic={}, key={}", event.getTopic(), event.getKey());
        } catch (Exception e) {
            throw new RuntimeException("Kafka publish failed for topic=" + event.getTopic(), e);
        }
    }

    void publishFallback(OutboxEvent event, Throwable t) {
        log.warn("Outbox CB open: skipping topic={}, key={}, reason={}", event.getTopic(), event.getKey(), t.getMessage());
    }
}
