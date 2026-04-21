package com.banking.account.repository;

import com.banking.account.domain.MoneyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MoneyAuditLogRepository extends JpaRepository<MoneyAuditLog, UUID> {
}
