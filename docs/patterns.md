# Design Patterns & Architecture Decisions

## Distributed Systems Patterns

### Outbox Pattern
Solves the dual-write problem: when a service must write to a database **and** publish to Kafka atomically.

**Implementation:** `TransactionService.transfer()` saves a `Transaction` (PENDING) and an `OutboxEvent` in a single `@Transactional` block. `OutboxPublisher` polls every second for unsent events and publishes them to Kafka — only then marking them `sent=true`.

**Why it matters:** Without this, a crash between the DB write and `kafkaTemplate.send()` would lose the event permanently. With Outbox, the event survives restarts — it will be retried until sent. Guarantees **at-least-once delivery**.

---

### Idempotency via Redis
Prevents duplicate side effects when clients retry requests (network timeouts, client bugs).

**Implementation:** Client sends `X-Idempotency-Key` header. Before processing, service checks Redis for `idempotency:{action}:{key}`. If found → `409 Conflict`. After successful processing → write key to Redis with 24h TTL.

**Used in:** deposit (account-service), transfer (transaction-service), registration (auth-service).

---

### Dead Letter Queue (DLQ)
Prevents a single bad message from blocking an entire Kafka partition forever.

**Implementation:** `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` on every `@KafkaListener`. After 3 retries with 1s backoff, the message is routed to `{topic}.DLT`. The original consumer continues processing other messages.

**Topics with DLQ:** `transaction.created.DLT`, `fraud.approved.DLT`, `fraud.rejected.DLT`, `balance.update.DLT`.

---

### Circuit Breaker (Resilience4j)
Prevents cascading failures when a downstream dependency (Kafka) is unavailable.

**States:** CLOSED (normal) → OPEN (fast-fail to fallback) → HALF-OPEN (probe calls) → CLOSED.

**Configuration:** sliding window of 10 calls, opens at 50% failure rate, waits 30s before half-opening, allows 3 probe calls.

**Two instances:**
- `kafka-outbox` on `OutboxPublisher.publishEvent()` — fallback silently skips the event (it stays `sent=false` and will be retried next cycle)
- `balance-update` on `BalanceUpdateConsumer.consume()` — fallback re-throws, triggering DLQ

---

### Token Bucket Rate Limiting
Protects all services from abuse at the API Gateway level.

**Implementation:** Spring Cloud Gateway `RedisRateLimiter`, IP-based `KeyResolver`. 20 requests/second per IP, burst capacity of 40. State stored in Redis — works correctly across multiple Gateway instances.

---

## Security Patterns

### Refresh Token Hashing
If the database is compromised, stolen plain-text refresh tokens would allow account takeover.

**Implementation:** `TokenHasher.hash(token)` computes SHA-256 via `MessageDigest`. Only the hash is stored in the `token_hash` column (Flyway V4 migration). The plain token is returned to the client once and never persisted.

On refresh: incoming token is hashed → looked up by hash. A leaked database contains only useless hashes.

---

### Stateless JWT + Gateway Auth
Downstream services contain zero authentication logic.

**Flow:** API Gateway validates the JWT locally (shared `JWT_SECRET`, no call to auth-service). On success it injects `X-User-Id` and `X-User-Role` headers. All downstream services read identity from these headers. `SecurityConfig` in downstream services is `permitAll()` — real enforcement happens only at the gateway.

---

### Opaque Refresh Tokens
Access tokens are JWTs (self-contained, verifiable without DB). Refresh tokens are opaque UUIDs stored in the database — explicit revocation is possible (logout deletes the row). Single-use rotation: every refresh call deletes the old token and issues a new one.

---

## Reliability Patterns

### Async Audit Logging (`@Async`)
Audit writes must not affect request latency or fail the primary operation.

**Implementation:** `@Async` on `MoneyAuditService.logDeposit()` / `logTransfer()` and `AuditService.log()`. Spring executes these in a separate thread pool. If the audit write fails, the primary transaction is already committed — this is an intentional trade-off (audit is secondary to the operation itself).

---

### Redis Caching (`@Cacheable` / `@CacheEvict`)
Avoids repeated DB reads for frequently accessed, rarely changing data.

**Implementation:** `AccountService.getBalance()` is annotated `@Cacheable(value="balances", key="#accountId")`. Any operation that modifies balance (`deposit`, `applyBalanceUpdate`, `close`) carries `@CacheEvict` or `@Caching` to invalidate the entry. `RedisCacheManager` is configured with `USE_BIG_DECIMAL_FOR_FLOATS` to prevent precision loss.

---

## Code Quality Patterns

### API Interface + Controller Split
Swagger/OpenAPI annotations pollute controller code and make it hard to read.

**Pattern:** Each controller implements an `{Name}Api` interface that carries all `@Operation`, `@ApiResponse`, and `@Parameter` annotations. The controller class contains only business logic — clean and readable.

---

### AppConstants in `common` Module
Magic strings scattered across services are a maintenance hazard — a typo in a Kafka topic name causes silent message loss.

**Pattern:** All shared constants (Kafka topic names, HTTP header names, Redis key prefixes, cache names) live in `AppConstants` with inner static classes (`Kafka`, `Headers`, `Redis`, `Cache`, `Security`). All services depend on `common`, so a rename is a single-point change and the compiler catches mismatches.

---

### RFC 7807 ProblemDetail
Inconsistent error response shapes force API consumers to handle multiple formats.

**Pattern:** `GlobalExceptionHandler` (`@RestControllerAdvice`) in every HTTP service returns Spring's `ProblemDetail` for all exceptions. Enabled via `spring.mvc.problemdetails.enabled: true`. Exception classes (`AuthException`, `AccountException`, `TransactionException`) use factory methods (`notFound()`, `conflict()`, `unauthorized()`) that construct the correct HTTP status — no status logic scattered in controllers.

---

### Database-per-Service
No shared databases between microservices — each service owns its schema exclusively.

**Four separate PostgreSQL instances:** `postgres-auth` (5436), `postgres-account` (5433), `postgres-transaction` (5434), `postgres-fraud` (5435). Cross-service data needs go through APIs or Kafka events — never direct DB queries.
