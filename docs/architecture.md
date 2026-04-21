# Architecture

## Services Overview

| Service | Port | Type | Database |
|---|---|---|---|
| **API Gateway** | 8080 | Spring Cloud Gateway (reactive) | — |
| **Auth Service** | 8081 | Spring Boot (servlet) | `postgres-auth` :5436 |
| **Account Service** | 8082 | Spring Boot (servlet) | `postgres-account` :5433 |
| **Transaction Service** | 8083 | Spring Boot (servlet) | `postgres-transaction` :5434 |
| **Fraud Service** | — | Spring Boot (Kafka-only) | `postgres-fraud` :5435 |

---

## Request Flow

```
Internet
    │
    ▼
API Gateway :8080
    │  ① Redis rate limiting — 20 req/s per IP, burst 40
    │  ② JWT signature validated locally (shared JWT_SECRET)
    │  ③ X-User-Id + X-User-Role headers injected
    │
    ├─── /api/v1/auth/**         ──► Auth Service :8081   (no JWT check — public)
    ├─── /api/v1/accounts/**     ──► Account Service :8082
    └─── /api/v1/transactions/** ──► Transaction Service :8083
```

Downstream services trust `X-User-Id` / `X-User-Role` headers. They run with `SecurityConfig permitAll()` — authentication enforcement happens exclusively at the Gateway.

---

## Async Transaction Pipeline

```
POST /api/v1/transactions/transfer
         │
         ▼
Transaction Service
  ① Idempotency check (Redis: idempotency:transfer:{key})
  ② Save Transaction (status=PENDING) ─┐
  ③ Save OutboxEvent                   ├─ single @Transactional
  └─────────────────────────────────────┘
         │
         ▼  OutboxPublisher @Scheduled every 1s
Kafka topic: transaction.created
         │
         ▼
Fraud Service
  Evaluates 4 rules (fail-fast):
    1. fromAccountId == toAccountId  → REJECT
    2. amount > 10,000               → REJECT
    3. sender sent > 10 tx in 1 hour → REJECT
    4. same account pair > 5 tx/day  → REJECT
  Saves FraudAlert (every transaction, regardless of result)
  Publishes to: fraud.approved  OR  fraud.rejected
         │
         ├──────────────────────────────────────────┐
         ▼                                          ▼
Transaction Service                         Account Service
  Consumes fraud.approved/rejected            Consumes balance.update
  Updates Transaction status                  Calls applyBalanceUpdate()
  COMPLETED or BLOCKED                        Debits from, credits to
  Publishes balance.update via Outbox         Evicts Redis balance cache
```

**Dead Letter Queues** at every Kafka consumer: 3 retries (1s backoff) → `topic.DLT`

---

## Module Structure

```
banking-simulator/
├── common/                        # Shared: BaseEntity, AppConstants, ErrorResponse
├── api-gateway/                   # Spring Cloud Gateway + JWT filter
├── auth-service/                  # Auth, JWT, refresh tokens, roles
├── account-service/               # Accounts, cards, balances, cache
├── transaction-service/           # Transfers, Outbox, Kafka producer/consumer
├── fraud-service/                 # Fraud rules, Kafka consumer only
├── monitoring/
│   ├── prometheus.yml             # Scrape config for all 5 services
│   └── grafana/
│       ├── provisioning/          # Auto-provisioned datasource + dashboard config
│       └── dashboards/            # banking-simulator.json (9 panels)
└── docker-compose.yml
```

### Common Module (`common`)
Shared library compiled into all services. Contains:
- `BaseEntity` — UUID primary key + `createdAt` / `updatedAt` timestamps (JPA `@MappedSuperclass`)
- `AppConstants` — compile-time constants: Kafka topic names, HTTP header names, Redis key prefixes, cache names
- `ErrorResponse` — standard error DTO

---

## Inter-Service Communication

| From | To | How | Topic / Endpoint |
|---|---|---|---|
| Gateway | Auth / Account / Transaction | HTTP (proxy) | `/api/v1/**` |
| Transaction | Fraud | Kafka | `transaction.created` |
| Fraud | Transaction | Kafka | `fraud.approved`, `fraud.rejected` |
| Fraud | Account | Kafka | `balance.update` |
| Account | — | Redis | Balance cache (`balances::` namespace) |
| Auth | — | Redis | Rate limiting, idempotency |
| Transaction | — | Redis | Idempotency |

Services never call each other's HTTP APIs — all cross-service data flows through Kafka. The only exception is the Gateway proxying client requests.

---

## Database Schema

### Auth Service — `postgres-auth` (port 5436)

#### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `email` | VARCHAR(255) UNIQUE | indexed |
| `password` | VARCHAR(255) | BCrypt hash |
| `first_name` | VARCHAR(100) | |
| `last_name` | VARCHAR(100) | |
| `role` | VARCHAR(20) | `USER` / `ADMIN` |
| `blocked` | BOOLEAN | default `false` |
| `created_at` / `updated_at` | TIMESTAMP | |

#### `refresh_tokens`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | `ON DELETE CASCADE` |
| `token_hash` | VARCHAR(512) UNIQUE | SHA-256 hex, indexed |
| `expires_at` | TIMESTAMP | 7-day TTL enforced in code |
| `created_at` / `updated_at` | TIMESTAMP | |

#### `audit_log`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | `ON DELETE SET NULL` |
| `action` | VARCHAR(100) | `REGISTER`, `LOGIN`, `LOGIN_FAILED`, `LOGIN_BLOCKED` |
| `ip_address` | VARCHAR(45) | IPv4 / IPv6 |
| `details` | TEXT | |
| `created_at` / `updated_at` | TIMESTAMP | indexed on `created_at` |

---

### Account Service — `postgres-account` (port 5433)

#### `accounts`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID | indexed (no FK — cross-service) |
| `currency` | VARCHAR(3) | `USD`, `EUR`, `UAH`, `GBP` |
| `status` | VARCHAR(10) | `ACTIVE` / `CLOSED` |
| `balance` | NUMERIC(19,4) | default 0 |
| `created_at` / `updated_at` | TIMESTAMP | |

#### `cards`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `account_id` | UUID FK → accounts | `ON DELETE CASCADE` |
| `card_number` | VARCHAR(19) UNIQUE | |
| `status` | VARCHAR(10) | `ACTIVE` / `BLOCKED` |
| `transaction_limit` | NUMERIC(19,4) | nullable |
| `created_at` / `updated_at` | TIMESTAMP | |

#### `money_audit_log`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID | indexed |
| `action` | VARCHAR(50) | `DEPOSIT` |
| `account_id` | UUID | |
| `amount` | NUMERIC(19,4) | |
| `currency` | VARCHAR(10) | |
| `details` | TEXT | |
| `created_at` / `updated_at` | TIMESTAMP | indexed on `created_at` |

---

### Transaction Service — `postgres-transaction` (port 5434)

#### `transactions`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `idempotency_key` | VARCHAR(64) UNIQUE | client-supplied |
| `sender_id` | UUID | indexed |
| `from_account_id` | UUID | indexed |
| `to_account_id` | UUID | indexed |
| `amount` | NUMERIC(19,4) | |
| `currency` | VARCHAR(3) | |
| `status` | VARCHAR(20) | `PENDING` → `COMPLETED` / `BLOCKED` |
| `failure_reason` | VARCHAR(255) | set on `BLOCKED` |
| `created_at` / `updated_at` | TIMESTAMP | |

#### `outbox_events`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `topic` | VARCHAR(255) | Kafka topic name |
| `key` | VARCHAR(255) | Kafka message key |
| `payload` | TEXT | JSON |
| `sent` | BOOLEAN | default `false` |
| `created_at` | TIMESTAMP | partial index on `(sent, created_at) WHERE sent = false` |
| `sent_at` | TIMESTAMP | nullable |

#### `money_audit_log`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID | indexed |
| `action` | VARCHAR(50) | `TRANSFER` |
| `transaction_id` | UUID | |
| `from_account_id` | UUID | |
| `to_account_id` | UUID | |
| `amount` | NUMERIC(19,4) | |
| `currency` | VARCHAR(10) | |
| `created_at` / `updated_at` | TIMESTAMP | |

---

### Fraud Service — `postgres-fraud` (port 5435)

#### `fraud_alerts`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `transaction_id` | UUID | indexed |
| `sender_id` | UUID | indexed |
| `from_account_id` | UUID | |
| `to_account_id` | UUID | |
| `amount` | NUMERIC(19,4) | |
| `currency` | VARCHAR(3) | |
| `approved` | BOOLEAN | `true` = passed, `false` = blocked |
| `reason` | VARCHAR(255) | fraud rule that triggered, or `null` if approved |
| `created_at` / `updated_at` | TIMESTAMP | |
