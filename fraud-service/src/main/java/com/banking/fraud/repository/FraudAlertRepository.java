package com.banking.fraud.repository;

import com.banking.fraud.domain.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    @Query("SELECT COUNT(a) FROM FraudAlert a WHERE a.senderId = :senderId AND a.createdAt > :after")
    long countRecentBySender(@Param("senderId") UUID senderId, @Param("after") LocalDateTime after);

    @Query("SELECT COUNT(a) FROM FraudAlert a WHERE a.fromAccountId = :from AND a.toAccountId = :to AND a.createdAt > :after")
    long countRecentByAccountPair(@Param("from") UUID fromAccountId, @Param("to") UUID toAccountId, @Param("after") LocalDateTime after);
}
