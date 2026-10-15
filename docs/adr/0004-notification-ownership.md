# ADR 0004: Notifications belong to the authenticated user

**Status:** accepted (breaking API change)

## Context

All non-admin endpoints were protected only by the shared `X-API-KEY`. Any holder of the key could read any
notification by id and enumerate ids. The API key identifies "a client application", not a user, so per-user
authorization was impossible.

## Decision

- `/notifications/**` requires a JWT (any role) **in addition to** the API key.
- Each notification stores `owner` (the JWT username; for campaign notifications the campaign's creator).
- `GET /notifications/{id}` returns **404** for another user's notification (not 403, so ids cannot be probed).
  `GET /notifications` lists only the caller's notifications. `ADMIN` sees everything.
- Idempotency keys are scoped per owner (ADR 0002).

## Consequences

- Clients that sent notifications with only the API key must now log in first (`POST /auth/login`).
- Recipients, templates and campaign *reads* still need only the API key; their writes need `ADMIN`.
- Notifications created before this change have no owner and are visible to `ADMIN` only.
