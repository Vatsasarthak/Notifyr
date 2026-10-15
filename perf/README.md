# Load test

`perf/k6/send-notifications.js` measures the notification path end to end:

1. **API side** — `POST /notifications/send` at a constant arrival rate (k6 `constant-arrival-rate`), each request
   with a unique `Idempotency-Key` (so the idempotency lookup, the database insert and the outbox insert are all
   exercised). Measured: request throughput, latency p50/p90/p95/p99, error rate, dropped iterations.
2. **Delivery side** — after the load phase the script polls the API until none of the run's notifications is
   `QUEUED` or `PROCESSING`; the runner then computes the **exact** end-to-end latency (accepted → delivered,
   `sent_at - created_at`) of every notification of the run in PostgreSQL (`percentile_cont`).

Each run registers a fresh `API_CLIENT` user, so its notifications can be counted through the normal API.

## How to run

```powershell
docker compose up -d --build          # the stack must be running and healthy
.\perf\run-load-test.ps1 -Rate 100 -Duration 60s [-MaxVus 500]
```

The script runs `grafana/k6:0.54.0` in Docker on the compose network (target `http://app:8080`), passes `API_KEY`
from `.env` without printing it, and writes two files per run to `perf/results/`:
`k6-rate<rate>-<timestamp>.json` (k6 summary: API side) and `delivery-rate<rate>-<timestamp>.json` (delivery
latency percentiles). k6 exits with code 99 when a threshold was crossed.

Notes on reading the output:

- k6's `iterations …/s` divides by the whole run *including* the delivery-drain teardown; request throughput below
  is therefore `successful requests / load duration`.
- Delivery latency is measured from the database rather than from the Prometheus histogram: an earlier attempt
  showed that bucket-interpolated quantiles were misleading at saturation (they hit the histogram's 30 s ceiling,
  which has since been raised to 15 min). `created_at` and `sent_at` both come from the application's clock.
- Delivery latency includes the outbox poll interval (≤ 100 ms) and any queueing behind a backlog.
- The p95 threshold of 500 ms and the 1 % error threshold are targets the run reports against, not guarantees.

## Workload and configuration

| Setting | Value |
|---------|-------|
| Request | `POST /notifications/send`, ~120-byte JSON body, JWT + API key, unique `Idempotency-Key` |
| Provider | mock email provider, no artificial delay (`MOCK_PROVIDER_DELAY_MS=0`) — measures the platform, not SMTP |
| App | 1 instance, default Tomcat (200 threads), Hikari pool 20, 4–8 notification consumers, prefetch 20 |
| Outbox relay | poll every 100 ms, batch 200, publisher confirms |
| Warm-up | 60 s at 100 req/s before the measured runs (discarded) |
| Runs | 60 s each at 100, 250, 500, 1000 and 2000 req/s, one after another; each run waits until its notifications are drained |
| Load generator | up to 2000 k6 VUs (`-MaxVus 2000`); if all VUs are busy, k6 drops iterations (reported) |

## Environment

| | |
|---|---|
| CPU | AMD Ryzen 5 5600H (6 cores / 12 threads), laptop |
| RAM | 15.4 GB host; Docker Desktop VM: 12 vCPU, 7.5 GiB |
| OS / Docker | Windows 11, Docker Desktop 29.6.1 (WSL2 kernel 6.18) |
| Software | Java 21 (Temurin JRE in container), Spring Boot 3.5.7, PostgreSQL 16, RabbitMQ 3.13, Redis 7, k6 0.54.0 |
| Co-located | k6, the app, PostgreSQL, RabbitMQ, Redis, Prometheus and Grafana all on the same laptop, plus unrelated containers of another project (idle) |

Because load generator and system under test share one laptop, these numbers describe this machine only; they
are not a capacity statement for a production deployment.

## Results

Measured on 2026-10-11, runs back to back after the warm-up (raw files in [`results/`](results/)).

> **Version note:** these runs used the code as of 2026-10-11 (Spring Boot 3.5.7). The 2026-10-15 audit upgraded
> dependencies (Spring Boot 3.5.16), masked personal data in logs and added an attempt check to the worker's claim
> `UPDATE`. These changes were not re-measured: the test machine was saturated by unrelated processes that day,
> which would have made new numbers meaningless. Re-run `perf/run-load-test.ps1` on an idle machine to refresh them.
"Accepted" = `202` responses; every accepted notification ended `SIMULATED` in every run (0 `FAILED`, 0 left pending).

| Target rate | Accepted (60 s) | API latency p50 / p95 / p99 | HTTP errors | Dropped iterations¹ | Delivery latency p50 / p95 / p99 | Backlog drained after load stopped |
|---:|---:|---|---:|---:|---|---:|
| 100 req/s | 6,001 | 4.5 / 6.1 / 7.8 ms | 0 % | 0 | 64 / 113 / 117 ms | 1.0 s |
| 250 req/s | 15,001 | 4.3 / 10.4 / 51.9 ms | 0 % | 0 | 78 / 127 / 445 ms | 1.0 s |
| 500 req/s | 30,000 | 5.1 / 12.8 / 19.1 ms | 0 % | 0 | 107 / 145 / 162 ms | 1.0 s |
| 1000 req/s | 60,001 | 11.9 / 67.9 / 377 ms | 0 % | 0 | 6.6 / 21.7 / 24.9 **s** | 26.7 s |
| 2000 req/s | 98,971 (≈ 1,650/s) | 918 ms / 1.5 s / 9.1 s | 0 % | 21,029 | 99 / 140 / 142 **s** | 142.5 s |

¹ k6 could not start the request because all 2000 virtual users were waiting for responses, i.e. the API could not
keep up with the offered rate (the run crossed the p95 < 500 ms threshold).

### Interpretation

- **Up to 500 req/s** (the highest rate tested below saturation) the whole path keeps up: API p95 ≤ 12.8 ms,
  accepted → delivered p95 ≤ 145 ms, no backlog. Delivery latency is dominated by the outbox poll interval (100 ms).
- **At 1000 req/s** the API still accepts everything (p95 68 ms) but delivery falls behind: 60,001 notifications took
  86.7 s to deliver, an effective **~690 deliveries/s**. The backlog is queued, not lost, and drained 27 s after the
  load stopped — this is the outbox absorbing a burst, as designed.
- **At 2000 req/s** the API saturates (≈ 1,650 accepted/s, p95 1.5 s, 21k requests not sent by k6); delivery again
  runs at roughly 480–700/s and needs 142 s to drain.
- **Bottleneck:** an earlier exploratory run at the same rates (Prometheus data, not in the table) showed the outbox
  relay peaking around 1,200 published messages/s while the app used ≈ 4.0–4.8 and PostgreSQL ≈ 2.2–2.6 of the 12 vCPUs
  (`docker stats` samples), i.e. the machine was not CPU-bound.
  The relay is a single thread per instance that waits for a broker confirm per batch, and it competes with the API
  for PostgreSQL. Likely next steps, not implemented or measured: several relay threads/instances (the
  `SKIP LOCKED` query already allows it), larger batches, or event-driven wake-up instead of polling.

These numbers are for one laptop running the load generator and every service together; they show the system's
behaviour and where it saturates, not production capacity.
