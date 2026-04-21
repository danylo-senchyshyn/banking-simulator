package com.banking.transaction.repository;

import com.banking.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findAllBySenderId(UUID senderId, Pageable pageable);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
