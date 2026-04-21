# Tech Stack

| What                       | How                                    | Why                                                  |
|----------------------------|----------------------------------------|------------------------------------------------------|
| Java 21                    | Core language                          | Records, virtual threads, modern APIs                |
| Spring Boot 3.3.4          | Application framework                  | Industry standard, fast to bootstrap                 |
| Spring Security + JWT      | Auth & authorization                   | Stateless auth, refresh token rotation               |
| Spring Data JPA            | Database access                        | Clean repositories, Flyway migrations                |
| PostgreSQL ×4              | Primary database                       | One DB per service (isolation)                       |
| Spring Kafka               | Event streaming                        | Async transaction/fraud flow                         |
| Redis                      | Cache + rate limiting + idempotency    | Fast in-memory ops, TTL support                      |
| Flyway                     | DB migrations                          | Version-controlled schema changes                    |
| Spring Cloud Gateway       | API Gateway                            | Reactive routing + JWT filter                        |
| Resilience4j               | Circuit breaker                        | Graceful degradation when Kafka is down              |
| Micrometer + OpenTelemetry | Distributed tracing + metrics          | Traces across all services via Jaeger                |
| Prometheus + Grafana       | Metrics collection + dashboards        | Custom counters/gauges, 9-panel dashboard            |
| Jaeger                     | Trace visualization                    | UI at `http://localhost:16686`                       |
| Docker Compose             | Local infrastructure                   | One command to run everything                        |
| jjwt 0.12.6                | JWT library                            | Token generation and parsing                         |
| Testcontainers             | Integration testing                    | Real PostgreSQL + Redis in tests                     |

## Code-Level Libraries & Patterns

| What | How | Why |
|---|---|---|
| MapStruct 1.5.5 | Entity → DTO mapping (`@Mapper`) | Compile-time generated, no reflection overhead |
| Lombok | Boilerplate reduction (`@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) | Cleaner entities and services |
| springdoc-openapi 2.6.0 | Swagger UI + OpenAPI 3 spec | Auto-generated interactive API docs at `/swagger-ui/index.html` |
| Java Records | DTOs / request-response objects | Immutable, concise, built-in equals/hashCode |
| `@ConfigurationProperties` | Typed config binding (e.g. `JwtProperties`) | Type-safe config instead of scattered `@Value` |
| API Interface + Controller | Swagger annotations in interface, logic in controller | Clean separation — no doc noise in controller |
| `ResponseEntity<T>` | Explicit HTTP response wrapping | Full control over status codes and headers |
| RFC 7807 ProblemDetail | `GlobalExceptionHandler` in all HTTP services | Standardised error schema, `spring.mvc.problemdetails.enabled: true` |
| `@Async` + `@EnableAsync` | Non-blocking side effects (audit logging, money audit) | Fire-and-forget — does not block request thread |
| `OncePerRequestFilter` | `CorrelationIdFilter` in servlet services | Guaranteed single execution per request |
| `@Cacheable` / `@CacheEvict` / `@Caching` | Declarative Redis caching via `RedisCacheManager` | No manual cache checks — Spring intercepts via proxy |
| Outbox pattern | `OutboxEvent` entity + `@Scheduled` poller (1s) | Atomically couple DB write + Kafka publish; survives Kafka restarts |
| Idempotency via Redis | `X-Idempotency-Key` header, Redis key TTL 24h | Prevents duplicate deposits and registrations |
| DLQ | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` | 3 retries → `topic.DLT`; failed messages preserved for inspection |
| Circuit Breaker | `@CircuitBreaker(name=...)` + fallback method (Resilience4j) | Open after 50% failures in 10-call window; wait 30s before retry |
| `AppConstants` in `common` | Kafka topics, HTTP headers, cache names, DLT suffixes | Compile-time constants shared across all services |
| SHA-256 token hashing | `TokenHasher.hash()` via `MessageDigest` | Refresh tokens stored as hash only — plain token never touches DB |
| Custom Micrometer metrics | `Counter.builder(...)`, `Gauge.builder(...)` in constructors | Business-level metrics beyond default Spring actuator ones |
| `spring.json.add.type.headers: false` | Kafka producer config | Don't embed Java type headers in messages |
| `spring.json.value.default.type` | Kafka consumer config | Deserialize to correct type without cross-service class resolution |
| JPQL `@Query` + `@Param` | Complex queries in repositories | Readable over long derived query names |
| `SimpleMeterRegistry` in tests | Passed to services under test | Avoids `NullPointerException` in constructors that register metrics |
| `lenient().when()` in Mockito | Stubs used only in constructors (e.g. Gauge lambda) | Prevents `UnnecessaryStubbingException` in strict mode |

