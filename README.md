# Idempotent Ledger Core (Transaction Engine)

A high-concurrency, ACID-compliant transaction processing microservice designed to handle distributed network retries and eliminate race conditions during financial state mutations.

## Architecture & Core Concepts
In distributed systems, network drops can cause clients to retry requests, leading to the "double-spend" problem. This microservice solves this by implementing an **Idempotency Layer** using Redis and **Pessimistic Locking** via PostgreSQL.

*   **Idempotency Key (Redis):** Every HTTP request must include a unique `Idempotency-Key` header. The system checks Redis (via `setIfAbsent`). If the key exists, the request is intercepted, and the cached response is returned without touching the database.
*   **Pessimistic Write Locking (PostgreSQL):** To prevent race conditions when two concurrent threads attempt to mutate the same wallet balance, JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` is utilized. This ensures database rows are strictly locked at the transaction level, guaranteeing ACID compliance.

## Tech Stack
*   **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Spring Web
*   **Persistence:** PostgreSQL (ACID relational data)
*   **Caching/Idempotency:** Redis (In-memory distributed lock)
*   **Tooling:** Lombok, Maven, Docker

## API Specification
`POST /api/v1/transactions/transfer`

**Headers:**
`Idempotency-Key: <UUID>`

**Payload:**
```json
{
  "senderId": 101,
  "receiverId": 102,
  "amount": 500.00
}
