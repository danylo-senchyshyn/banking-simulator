# Running the Project

## Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Docker Desktop | 4.x+ | Required for all infrastructure containers |
| Docker Compose | v2.x (`docker compose`) | Bundled with Docker Desktop |
| Java | 21 | Only needed for local development / running tests outside Docker |
| Maven | 3.9+ | Only needed for local development |

> **All services run inside Docker.** Java and Maven are not required just to start the stack.

---

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/your-username/banking-simulator.git
cd banking-simulator

# 2. Configure secrets
cp .env.example .env
# Edit .env — set JWT_SECRET (any long random string) and DB passwords

# 3. Start everything
docker-compose --profile fraud up -d

# 4. Wait ~30 seconds for all services to initialise, then open:
# Auth Swagger:        http://localhost:8081/swagger-ui/index.html
# Account Swagger:     http://localhost:8082/swagger-ui/index.html
# Transaction Swagger: http://localhost:8083/swagger-ui/index.html
# Grafana:             http://localhost:3000  (admin / admin)
# Jaeger:              http://localhost:16686
```

---

## Environment Variables (`.env`)

| Variable | Example | Description |
|---|---|---|
| `JWT_SECRET` | `your-256-bit-secret` | Shared secret for HS256 JWT signing — must be the same in Gateway and Auth Service |
| `AUTH_DB_PASSWORD` | `authpass` | Password for `postgres-auth` |
| `ACCOUNT_DB_PASSWORD` | `accountpass` | Password for `postgres-account` |
| `TRANSACTION_DB_PASSWORD` | `txpass` | Password for `postgres-transaction` |
| `FRAUD_DB_PASSWORD` | `fraudpass` | Password for `postgres-fraud` |
| `REDIS_PASSWORD` | `redispass` | Redis password (can be empty for local dev) |

See `.env.example` for the full list with blank values.

---

## Docker Compose Profiles

The project uses profiles to start subsets of the stack:

| Profile | Services included | Use case |
|---|---|---|
| *(none)* | Gateway, Auth, Redis, Postgres-auth, Jaeger, Prometheus, Grafana | Auth-only development |
| `account` | + Account Service, Postgres-account | Account development |
| `transaction` | + Transaction Service, Postgres-transaction, Kafka, Zookeeper | Transaction development |
| `fraud` | All of the above + Fraud Service, Postgres-fraud | Full stack |

```bash
# Full stack (recommended)
docker-compose --profile fraud up -d

# Stop everything
docker-compose --profile fraud down

# Stop and delete all volumes (fresh start)
docker-compose --profile fraud down -v
```

---

## Port Reference

| Service | Host Port | Notes |
|---|---|---|
| API Gateway | 8080 | All client traffic goes here |
| Auth Service | 8081 | Direct access for Swagger |
| Account Service | 8082 | Direct access for Swagger |
| Transaction Service | 8083 | Direct access for Swagger |
| postgres-auth | 5436 | Non-standard port to avoid conflict with local PostgreSQL |
| postgres-account | 5433 | |
| postgres-transaction | 5434 | |
| postgres-fraud | 5435 | |
| Redis | 6379 | |
| Kafka | 9092 | |
| Zookeeper | 2181 | |
| Jaeger UI | 16686 | Distributed traces |
| Jaeger OTLP | 4318 | OTLP/HTTP receiver |
| Prometheus | 9090 | |
| Grafana | 3000 | admin / admin |

---

## Running Tests

Tests require Docker running (Testcontainers pulls PostgreSQL and Redis images automatically on first run).

```bash
# All tests in a specific service
mvn test -pl auth-service
mvn test -pl account-service
mvn test -pl transaction-service
mvn test -pl fraud-service

# All services at once
mvn test -pl auth-service,account-service,transaction-service,fraud-service

# Skip integration tests (unit + controller only)
mvn test -pl auth-service -Dgroups='!integration'
```

First run will pull `postgres:16-alpine` and `redis:7-alpine` Docker images (~200 MB total).

---

## Rebuilding After Code Changes

```bash
# Rebuild and restart a single service
docker-compose --profile fraud up -d --build auth-service

# Rebuild all services
docker-compose --profile fraud up -d --build

# Rebuild common module first (required if AppConstants changed)
mvn install -pl common -q
docker-compose --profile fraud up -d --build
```

---

## Verifying the Full Async Flow

```bash
# 1. Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123","firstName":"Alice","lastName":"Smith"}'

# 2. Login → copy accessToken
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

# 3. Create two accounts (use accessToken from step 2)
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"currency":"USD"}'

# 4. Deposit into the source account
curl -X POST http://localhost:8080/api/v1/accounts/{accountId}/deposit \
  -H "Authorization: Bearer {token}" \
  -H "X-Idempotency-Key: deposit-001" \
  -H "Content-Type: application/json" \
  -d '{"amount":"500.00"}'

# 5. Transfer
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer {token}" \
  -H "X-Idempotency-Key: transfer-001" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"{from}","toAccountId":"{to}","amount":"100.00","currency":"USD"}'

# 6. Check transaction status (should become COMPLETED within ~3s)
curl http://localhost:8080/api/v1/transactions/{transactionId} \
  -H "Authorization: Bearer {token}"
```

Trace the full flow end-to-end in Jaeger at `http://localhost:16686`.

---

## Logs

All services output structured JSON logs. To follow logs for a specific service:

```bash
docker-compose logs -f transaction-service
docker-compose logs -f fraud-service
```

Every log line includes `traceId`, `spanId`, `correlationId` — paste a `traceId` into Jaeger to see the full distributed trace.
