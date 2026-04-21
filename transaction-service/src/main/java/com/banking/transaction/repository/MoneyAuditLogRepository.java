package com.banking.transaction.repository;

import com.banking.transaction.domain.MoneyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MoneyAuditLogRepository extends JpaRepository<MoneyAuditLog, UUID> {
}
