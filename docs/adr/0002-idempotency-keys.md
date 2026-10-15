# ADR 0002: Idempotency keys enforced by PostgreSQL, cached in Redis

**Status:** accepted

## Context

Clients retry `POST /notifications/send` after timeouts. Without idempotency a retry sends the same message twice.
The first implementation stored keys only in Redis with a check-then-set sequence: concurrent requests could both
pass the check, a Redis restart or eviction forgot keys, keys were global across users, and reusing a key with a
different body silently returned the first response.

## Decision

- Keys are scoped per caller: unique index `(owner, idempotency_key)` on `notifications`. The database is the
  **source of truth**; two concurrent inserts with the same key cannot both succeed — the loser gets a unique
  violation, and the service returns the winner's notification.
- A SHA-256 hash of the request body is stored with the key. Reusing a key with a different body returns
  **409 Conflict**.
- Redis caches the first response for 24 h under `idempotency:sha256(owner, key, bodyHash)` (components separated by
  a NUL byte, so different owner/key splits cannot collide), so replays usually avoid the database. Redis errors are
  logged and ignored: idempotency still holds through the database.
- Redis is used only as this cache (plain `SET` with TTL / `GET`); there are no Lua scripts. Every atomic state change
  is a conditional SQL `UPDATE` or a unique constraint in PostgreSQL.

## Consequences

- Duplicate suppression survives Redis data loss and works across application instances
  (`ReliabilityIntegrationTest`: 20 concurrent identical requests → 1 row).
- The database keeps keys for the lifetime of the notification (no expiry), which is stricter than the usual
  24 h window; a client cannot reuse a key later.
- A replay returns the notification's current state when served from the database, or the original response
  when served from Redis.
