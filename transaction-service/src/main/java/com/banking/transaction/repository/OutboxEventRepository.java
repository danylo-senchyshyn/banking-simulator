package com.banking.transaction.repository;

import com.banking.transaction.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50BySentFalseOrderByCreatedAtAsc();

    long countBySentFalse();
}
