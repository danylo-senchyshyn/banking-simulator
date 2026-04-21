# Security

## Authentication & Authorization

### JWT Access Tokens
- HS256-signed, 15-minute expiry
- Payload carries `userId`, `email`, `role`
- Validated **only at the API Gateway** — downstream services never touch JWTs
- Secret shared via `JWT_SECRET` environment variable (never hardcoded)

### Opaque Refresh Tokens
- UUID v4 generated at login/register
- **SHA-256 hashed before storage** — `token_hash` column, Flyway V4 migration
- Plain token returned to client exactly once; never stored in plaintext anywhere
- Single-use rotation: refresh call deletes the old token row and issues a new one
- 7-day expiry enforced in DB (`expires_at` column checked before use)
- Full logout: `DELETE FROM refresh_tokens WHERE user_id = ?`

### Role-Based Access Control
- Two roles: `USER`, `ADMIN`
- `ADMIN` endpoints: block/unblock users, list all users, change user roles
- `USER` endpoints: view own profile, change own password
- Role embedded in JWT → injected as `X-User-Role` header by Gateway → enforced via `@PreAuthorize` in auth-service

---

## Network Security

### API Gateway as Security Perimeter
All public traffic enters through the Gateway (port 8080). Internal services (8081–8084) are not exposed to the internet in production — only accessible within the Docker network.

```
Internet → API Gateway (8080)
               ↓ JWT validated
               ↓ X-User-Id / X-User-Role injected
           Auth Service    (8081) — internal
           Account Service (8082) — internal
           Transaction Service (8083) — internal
           Fraud Service   (8084) — internal, Kafka-only
```

### Downstream Services Trust Headers
Downstream services read `X-User-Id` from the header injected by the Gateway. They do not accept user-supplied identity headers from untrusted sources — in production this is enforced by network policy (only the Gateway can reach downstream services).

---

## Rate Limiting & Brute-Force Protection

### Gateway-Level Rate Limiting
`RedisRateLimiter` on all routes: **20 requests/second per IP**, burst of 40. Implemented via token bucket algorithm in Redis. Excess requests receive `429 Too Many Requests` immediately.

### Login Brute-Force Protection
Tracked per IP in Redis:
1. Failed login → `INCR login:attempts:{ip}`, set TTL 15 min
2. On 5th failure → set `login:blocked:{ip}` with 15-min TTL, delete attempts key
3. Successful login → delete both keys (`clearAttempts`)
4. Every login first checks `login:blocked:{ip}` — blocked IPs receive `429` before any DB query

---

## Data Security

### Password Storage
BCrypt with Spring Security's `PasswordEncoder`. No plain passwords stored anywhere — not in logs, not in DB, not in audit trails.

### Secrets Management
All credentials (DB passwords, JWT secret, Redis password) are in `.env` file (gitignored). `.env.example` documents required variables without values. Loaded via `spring-dotenv` — no secrets in `application.yml`.

### Sensitive Data in Logs
`@Slf4j` logs use structured fields, never interpolating passwords or tokens. Log format: `email={}`, `userId={}` — never `password={}`.

---

## Audit Trail

### Auth Audit Log (`audit_log` table)
Every significant auth event is recorded asynchronously (`@Async`):
- `REGISTER`, `LOGIN`, `LOGIN_FAILED`, `LOGIN_BLOCKED`
- Fields: `userId`, `action`, `ip`, `details`, `createdAt`

### Money Audit Log (`money_audit_log` table)
Every financial operation is recorded asynchronously (`@Async`):
- **Deposit** (account-service): `userId`, `action=DEPOSIT`, `accountId`, `amount`, `currency`
- **Transfer** (transaction-service): `userId`, `action=TRANSFER`, `transactionId`, `fromAccountId`, `toAccountId`, `amount`, `currency`

Both audit logs are fire-and-forget — failures do not roll back the primary operation, but every completed operation is guaranteed to have a corresponding log entry.

---

## Input Validation

Bean Validation (`jakarta.validation`) on all request DTOs:
- `@NotNull`, `@NotBlank` — reject missing required fields → `400 Bad Request`
- `@Email` — reject malformed email addresses
- `@DecimalMin("0.01")` on deposit/transfer amounts — reject zero or negative values
- `@Size` on passwords — enforce minimum length

All validation errors are returned as RFC 7807 `ProblemDetail` with field-level details.
