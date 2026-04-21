# Development Roadmap

## Phase 1 — Infrastructure ✅
- Multi-module Maven (parent POM + 6 modules: `common`, `api-gateway`, `auth-service`, `account-service`, `transaction-service`, `fraud-service`)
- Docker Compose: PostgreSQL ×4, Redis, Kafka, Zookeeper, Jaeger — one command to run everything
- Shared `common` module: `BaseEntity` (UUID PK + timestamps), `ErrorResponse`, `AppConstants`
- Flyway migrations per service, `.env` via spring-dotenv, multi-stage Dockerfile (layered JAR)

## Phase 2 — Auth Service ✅
- Registration + login with BCrypt password hashing
- JWT access tokens (15 min) + opaque refresh token rotation (7 days, single-use, stored in DB)
- Roles: `USER` / `ADMIN` with Spring Security `@PreAuthorize`
- Login brute-force protection: 5 failed attempts → IP blocked 15 min via Redis
- `@Async` audit logging to `audit_log` table
- Admin endpoints: block/unblock users, list users, change roles
- Swagger UI at `http://localhost:8081/swagger-ui/index.html`

## Phase 3 — API Gateway ✅
- Spring Cloud Gateway (reactive) on port 8080
- JWT validated locally (shared `JWT_SECRET`) — zero calls to auth-service
- Injects `X-User-Id` / `X-User-Role` headers for all downstream services
- Routes: `/api/v1/auth/**` → 8081, `/api/v1/accounts/**` → 8082, `/api/v1/transactions/**` → 8083

## Phase 4 — Account Service ✅
- Account CRUD (create, get, close) with multi-currency (USD, EUR, UAH, GBP)
- Card management (issue, block, set per-card transaction limit)
- Balance reads cached in Redis (`@Cacheable` / `@CacheEvict` / `@Caching` via `RedisCacheManager`)
- Deposit endpoint (`POST /api/v1/accounts/{id}/deposit`)
- Swagger UI at `http://localhost:8082/swagger-ui/index.html`

## Phase 5 — Transaction Service ✅
- Money transfers with `X-Idempotency-Key` support
- Async flow: save PENDING → publish `transaction.created` → consume fraud result → update status → publish `balance.update`
- Transaction history paginated by `senderId`
- Swagger UI at `http://localhost:8083/swagger-ui/index.html`

## Phase 6 — Fraud Service ✅
- Kafka consumer only — no HTTP endpoints
- 4 fraud rules evaluated in order (fail-fast): same-account transfer, amount > 10,000, >10 tx/hour per sender, >5 tx/day between same account pair
- Saves `FraudAlert` for every transaction (audit trail)
- Publishes result to `fraud.approved` or `fraud.rejected`

## Phase 7 — Observability ✅
- Distributed tracing: Micrometer → OpenTelemetry → Jaeger across all 5 services (100% sampling)
- Structured JSON logging: `logstash-logback-encoder` in all services
- `CorrelationIdFilter` in all services — propagates `X-Correlation-Id` through every log line
- Every log line includes: `traceId`, `spanId`, `correlationId`, `service`, `timestamp`
- Full E2E flow verified in Docker: Register → Deposit → Transfer → COMPLETED → balances updated

## Phase 8 — CI/CD (GitHub Actions) ✅
- GitHub Actions workflow: triggered on every push and pull request to `main`
- Jobs: `build` (compile + unit tests) and `integration-test` (Testcontainers — requires Docker)
- Matrix build across Java 21
- Workflow badge in README

## Phase 9 — Unit & Controller Tests ✅
- `@ExtendWith(MockitoExtension)` unit tests for all service classes
- `@WebMvcTest` + `MockMvc` controller tests for all HTTP endpoints
- **98 tests, 0 failures** across auth, account, transaction, fraud services

## Phase 10 — API Hardening ✅
- API versioning — all routes prefixed `/api/v1/**`
- RFC 7807 `ProblemDetail` error responses via `GlobalExceptionHandler` in all HTTP services
- Exception factory methods (`notFound()`, `conflict()`, `unauthorized()`) — status logic centralized in exception classes

## Phase 11 — Reliability: Idempotency & Outbox ✅
- Idempotency on deposit and registration via `X-Idempotency-Key` header (Redis, TTL 24h)
- **Outbox pattern** in transaction-service: `Transaction` + `OutboxEvent` written atomically; `OutboxPublisher` `@Scheduled` poller (1s) guarantees Kafka delivery

## Phase 12 — Reliability: DLQ & Rate Limiting ✅
- DLQ on all Kafka consumers: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (3 retries, 1s backoff → `topic.DLT`)
- **Topics:** `transaction.created.DLT`, `fraud.approved.DLT`, `fraud.rejected.DLT`, `balance.update.DLT`
- API Gateway rate limiting: `RedisRateLimiter` (20 req/s per IP, burst 40)

## Phase 13 — Integration Tests (Testcontainers) ✅
- Real PostgreSQL + Redis containers per service via `@Testcontainers` + `@DynamicPropertySource`
- Tests cover: DB persistence, Redis caching, idempotency, Outbox atomicity, transaction isolation
- **21 integration tests** across auth, account, transaction services
- **Total: 119 tests, 0 failures**

## Phase 14 — Circuit Breaker (Resilience4j) ✅
- `@CircuitBreaker(name="kafka-outbox")` on `OutboxPublisher` — graceful skip when Kafka is down
- `@CircuitBreaker(name="balance-update")` on `BalanceUpdateConsumer` — fallback re-throws to trigger DLQ
- Config: sliding window 10, threshold 50%, wait 30s, half-open 3 probes
- CB state exposed via `/actuator/circuitbreakers` and `/actuator/health`
- **Total: 123 tests, 0 failures**

## Phase 15 — Metrics & Dashboards (Prometheus + Grafana) ✅
- `micrometer-registry-prometheus` in all 5 services; `/actuator/prometheus` endpoint exposed
- Custom Counters: `transaction.created.count`, `transaction.completed.count`, `transaction.blocked.count`, `fraud.rejected.count`
- Custom Gauge: `outbox.pending.size` (reads `countBySentFalse()` at scrape time — zero DB overhead)
- Prometheus scrape config (`monitoring/prometheus.yml`) for all 5 services
- Grafana auto-provisioned (datasource + dashboard) — no manual setup required
- **9-panel dashboard:** RPS, p99 latency, 5xx error rate, transaction counters, fraud rate, outbox backlog, CB state, JVM heap, Kafka consumer lag

## Phase 16 — Security Hardening ✅
- **Refresh token hashing:** SHA-256 via `MessageDigest` — only `token_hash` stored in DB; plain token returned to client once (Flyway V4 migration)
- **Money audit log:** `MoneyAuditLog` entity + `MoneyAuditService` (`@Async`) in account-service and transaction-service — every deposit and transfer logged asynchronously
- `AppConstants` audit: all hardcoded Kafka topics, Redis key prefixes, HTTP headers, cache names moved to `common` module — compile-time safety across all services
