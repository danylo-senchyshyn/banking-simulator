package com.banking.fraud.service;

import com.banking.common.AppConstants;
import com.banking.fraud.service.event.FraudResultEvent;
import com.banking.fraud.service.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionConsumer {

    private final FraudService fraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = AppConstants.Kafka.TRANSACTION_CREATED, groupId = "fraud-service")
    public void consume(TransactionCreatedEvent event) {
        log.info("Received transaction: id={}, amount={} {}", event.transactionId(), event.amount(), event.currency());

        FraudService.FraudResult result = fraudService.evaluate(event);

        String topic = result.approved() ? AppConstants.Kafka.FRAUD_APPROVED : AppConstants.Kafka.FRAUD_REJECTED;
        kafkaTemplate.send(topic, event.transactionId().toString(),
                new FraudResultEvent(event.transactionId(), result.approved(), result.reason()));

        log.info("Published fraud result to {}: transactionId={}", topic, event.transactionId());
    }
}
