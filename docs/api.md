# API Reference

All endpoints are versioned under `/api/v1`. Every HTTP service has interactive Swagger UI.

| Service | Swagger UI |
|---|---|
| Auth Service | `http://localhost:8081/swagger-ui/index.html` |
| Account Service | `http://localhost:8082/swagger-ui/index.html` |
| Transaction Service | `http://localhost:8083/swagger-ui/index.html` |

---

## Headers

| Header | Required by | Description |
|---|---|---|
| `Authorization: Bearer {token}` | Protected endpoints | JWT access token (15 min) |
| `X-Idempotency-Key` | Register, Deposit, Transfer | Client-generated unique key; duplicate requests within 24h return `409` |
| `X-User-Id` | Injected by Gateway | UUID of the authenticated user — set by Gateway, never by the client |
| `X-User-Role` | Injected by Gateway | `USER` or `ADMIN` — set by Gateway |
| `X-Correlation-Id` | All requests | Propagated through all services for log correlation; auto-generated if absent |

---

## Auth Service — `/api/v1/auth`

### Public endpoints

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/auth/register` | Register new user | `201` + token pair |
| `POST` | `/auth/login` | Login with email + password | `200` + token pair |
| `POST` | `/auth/refresh` | Exchange refresh token for new token pair | `200` + token pair |
| `POST` | `/auth/logout` | Revoke all refresh tokens for current user | `204` |

**Register** accepts `X-Idempotency-Key` — duplicate registrations within 24h return `409 Conflict`.

**Login** brute-force: 5 failed attempts per IP → `429 Too Many Requests` for 15 minutes.

**Refresh tokens** are single-use — each call invalidates the old token and issues a new one.

### User endpoints — `/api/v1/users` (requires `USER` role)

| Method | Path | Description | Success |
|---|---|---|---|
| `GET` | `/users/me` | Get own profile | `200` |
| `PUT` | `/users/me/password` | Change own password | `200` |

### Admin endpoints — `/api/v1/admin` (requires `ADMIN` role)

| Method | Path | Description | Success |
|---|---|---|---|
| `GET` | `/admin/users` | List all users | `200` |
| `PUT` | `/admin/users/{id}/block` | Block or unblock a user (`?blocked=true/false`) | `200` |
| `PUT` | `/admin/users/{id}/role` | Change user role (`USER` / `ADMIN`) | `200` |

---

## Account Service — `/api/v1/accounts`

All endpoints require `X-User-Id` header (injected by Gateway from JWT).

### Accounts

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/accounts` | Create account (body: `currency`) | `201` |
| `GET` | `/accounts` | List own accounts | `200` |
| `GET` | `/accounts/{id}` | Get account by ID | `200` |
| `DELETE` | `/accounts/{id}` | Close account (balance must be zero) | `200` |
| `POST` | `/accounts/{id}/deposit` | Deposit funds (body: `amount`; optional `X-Idempotency-Key`) | `200` |

**Deposit** accepts `X-Idempotency-Key` — duplicate requests within 24h return `409 Conflict`.

**Close** returns `409 Conflict` if balance is non-zero.

### Cards — `/api/v1/accounts/{accountId}/cards`

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/accounts/{accountId}/cards` | Issue a new card | `201` |
| `GET` | `/accounts/{accountId}/cards` | List cards for account | `200` |
| `PUT` | `/accounts/{accountId}/cards/{cardId}/block` | Block or unblock card | `200` |
| `PUT` | `/accounts/{accountId}/cards/{cardId}/limit` | Set per-card transaction limit | `200` |

---

## Transaction Service — `/api/v1/transactions`

All endpoints require `X-User-Id` header (injected by Gateway from JWT).

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/transactions/transfer` | Initiate a transfer (requires `X-Idempotency-Key`) | `202` |
| `GET` | `/transactions` | List own transactions (paginated) | `200` |
| `GET` | `/transactions/{id}` | Get transaction by ID | `200` |

**Transfer** is asynchronous — returns `202 Accepted` with `status=PENDING`. The final status (`COMPLETED` or `BLOCKED`) is determined after fraud evaluation (Kafka pipeline).

**Transfer** body:
```json
{
  "fromAccountId": "uuid",
  "toAccountId": "uuid",
  "amount": "150.00",
  "currency": "USD"
}
```

---

## Error Responses

All services return [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Duplicate request",
  "instance": "/api/v1/accounts/123/deposit"
}
```

| Status | Meaning |
|---|---|
| `400` | Validation error — missing or invalid request field |
| `401` | Missing, expired, or invalid token |
| `403` | Insufficient role permissions |
| `404` | Resource not found or not owned by current user |
| `409` | Conflict — duplicate idempotency key, email taken, non-zero balance on close |
| `429` | Rate limit exceeded (Gateway) or IP blocked (login brute-force) |
