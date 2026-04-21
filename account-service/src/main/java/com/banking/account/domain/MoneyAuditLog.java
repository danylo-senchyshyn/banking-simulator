package com.banking.account.domain;

import com.banking.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "money_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoneyAuditLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String action;

    @Column(name = "account_id")
    private UUID accountId;

    private BigDecimal amount;

    private String currency;

    private String details;
}
