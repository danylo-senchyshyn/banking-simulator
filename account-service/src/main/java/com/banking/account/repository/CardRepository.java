package com.banking.account.repository;

import com.banking.account.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByAccountId(UUID accountId);

    Optional<Card> findByIdAndAccountId(UUID id, UUID accountId);
}
