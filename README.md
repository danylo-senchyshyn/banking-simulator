# Banking Simulator

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-green.svg)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-event--driven-black.svg)
![Testcontainers](https://img.shields.io/badge/Testcontainers-123%20tests-blue.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)
![build](https://github.com/danylo-senchyshyn/banking-simulator/actions/workflows/ci.yml/badge.svg)

A production-grade backend banking system built as a deep-dive into microservices, event-driven architecture, and distributed systems patterns. Pure Java backend — no frontend. Every architectural decision was studied before implementation and is fully documented.

> **Built entirely with [Claude Code](https://claude.ai/code)** — using multi-agent orchestration, parallel task execution, and iterative planning across 16 development phases. Every pattern was studied and understood before implementation; Claude Code handled the execution, compressing months of boilerplate into focused engineering sessions.

---

## What It Does

Users register, authenticate, open multi-currency bank accounts, and transfer money between them. Every transfer is asynchronously evaluated for fraud before being approved or blocked. The full async pipeline is observable end-to-end through distributed traces, structured logs, and a live Grafana dashboard.

The system is intentionally over-engineered for a learning project — every production pattern (Outbox, DLQ, Circuit Breaker, Idempotency, Token Hashing, Testcontainers) is present because it was studied and understood, not because the scale demands it.

---

## Documentation

| | |
|---|---|
| [Architecture & DB Schema](docs/architecture.md) | Service map, request flow, async pipeline, all database tables |
| [API Reference](docs/api.md) | All endpoints, headers, request/response formats, Swagger UI links |
| [Design Patterns](docs/patterns.md) | Every pattern implemented — how it works and why |
| [Security](docs/security.md) | Auth flow, token hashing, rate limiting, audit trail |
| [Tech Stack](docs/tech-stack.md) | Full stack, code-level libraries, infrastructure ports |
| [Running the Project](docs/running.md) | Prerequisites, env vars, Docker Compose, test commands |
| [Development Roadmap](docs/roadmap.md) | 16-phase build history |

---

## Stack at a Glance

**Java 21 · Spring Boot 3.3.4 · PostgreSQL ×4 · Apache Kafka · Redis · Spring Cloud Gateway · Resilience4j · Micrometer / OpenTelemetry · Prometheus · Grafana · Jaeger · Testcontainers · Flyway · Docker Compose**

---

## Quick Start

```bash
cp .env.example .env          # set JWT_SECRET and DB passwords
docker-compose --profile fraud up -d
```

| | |
|---|---|
| Auth Swagger | `http://localhost:8081/swagger-ui/index.html` |
| Account Swagger | `http://localhost:8082/swagger-ui/index.html` |
| Transaction Swagger | `http://localhost:8083/swagger-ui/index.html` |
| Jaeger | `http://localhost:16686` |
| Grafana | `http://localhost:3000` · admin / admin |

Full setup guide: [docs/running.md](docs/running.md)

---

## Testing

**123 tests, 0 failures** — unit, controller (`@WebMvcTest`), and integration (Testcontainers with real PostgreSQL + Redis).

```bash
mvn test -pl auth-service,account-service,transaction-service,fraud-service
```
