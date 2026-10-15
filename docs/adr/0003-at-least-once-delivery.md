# ADR 0003: At-least-once delivery with atomic claims, leases and a recovery job

**Status:** accepted

## Context

Messages can be duplicated (outbox republish, broker redelivery, recovery job) and workers can die mid-delivery.
Exactly-once delivery to an external provider (SMTP, Twilio) is not achievable: if the process dies after the
provider accepted a message but before we recorded it, we cannot know whether it was sent.

## Decision

- **Claim before sending.** The worker moves a notification `QUEUED → PROCESSING` with a conditional `UPDATE`
  (`NotificationRepository.claim`). Only one worker wins; duplicates of a message for a notification that is
  being processed or already finished are acknowledged and skipped.
- **Job identity = (notification id, attempt).** Every message carries the attempt (`retryCount`) it was
  published for, and the claim requires it to equal the row's current `retry_count`. A copy of an earlier
  attempt (redelivered after a retry was already scheduled) is skipped, so it cannot bypass the retry delay.
- **No transaction around the provider call.** Claim (tx 1) → provider call → record outcome (tx 2). Outcome
  transitions are also conditional on `PROCESSING`.
- **Lease.** A `PROCESSING` row older than 5 minutes is considered abandoned. `NotificationRecoveryJob` (every
  minute) puts it back to `QUEUED` and counts it as an attempt, so a message that keeps crashing workers ends
  `FAILED` rather than looping. It also re-publishes `QUEUED` rows untouched for 15 minutes that have no pending
  outbox message (lost message), and re-dispatches campaigns stuck in `RUNNING` for 15 minutes without a
  pending dispatch message (the fan-out is idempotent: it only runs while `RUNNING`, under a row lock).
- **Bounded retries.** Transient failures retry after 30 s, 2 min and 5 min (TTL queues that dead-letter back to
  the main queue), then `FAILED` + dead-letter queue. Permanent failures (missing address, provider not
  configured, Twilio 4xx) skip retries.
- **Listener errors** (e.g. database down) are retried 3 times in-process, then the message is republished to
  the DLQ; messages are never requeued in a loop (`default-requeue-rejected: false`).

## Consequences

- Guarantee: every accepted notification eventually reaches a terminal state (`SENT`/`SIMULATED`/`FAILED`) as long
  as PostgreSQL and RabbitMQ eventually come back.
- A notification can be delivered **more than once** only if a worker dies (or exceeds the lease) after the
  provider accepted it and before the outcome was committed. Duplicate *messages* alone never cause duplicate
  deliveries (`ReliabilityIntegrationTest`: 10 duplicate messages → provider called once).
- The lease must stay longer than the slowest provider call (SMTP/Twilio timeouts are 10 s).
