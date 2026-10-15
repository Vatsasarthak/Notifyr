# ADR 0006: The first administrator comes from configuration

**Status:** accepted (supersedes the "first ADMIN may self-register" rule)

## Context

`POST /auth/register` is public. It used to accept `"role": "ADMIN"` while no ADMIN existed, so the operator could
bootstrap an administrator. That left two problems:

- on a freshly deployed instance, **anyone** who reached the endpoint before the operator became administrator;
- the check (`no ADMIN exists`) and the insert were not atomic, so two concurrent requests could both succeed.

## Decision

- The public endpoint never grants `ADMIN`. Registering an ADMIN requires the Bearer token of an existing ADMIN;
  everyone else gets `403`.
- `AdminBootstrap` (an `ApplicationRunner`) creates the first ADMIN at startup from `BOOTSTRAP_ADMIN_USERNAME` /
  `BOOTSTRAP_ADMIN_PASSWORD` — only while no ADMIN exists. It refuses passwords shorter than 12 characters and
  will not promote an existing non-admin account. A concurrent start of a second instance is harmless (unique
  username).

## Consequences

- Operators must set the two variables for the first start (they are in `.env.example`); after that they can be
  left empty.
- Without them the application still starts, logs a warning, and admin-only endpoints are unusable until an ADMIN
  exists.
