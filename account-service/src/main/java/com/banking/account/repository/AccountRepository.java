package com.banking.account.repository;

import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByUserId(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, AccountStatus status);
}
