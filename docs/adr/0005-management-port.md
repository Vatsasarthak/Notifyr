# ADR 0005: Actuator on a separate management port

**Status:** accepted

## Context

`/actuator/prometheus` and `/actuator/health` were public on the API port. Metrics reveal traffic patterns,
endpoint names and internals. Protecting them with the JWT would break Prometheus scraping (tokens expire).

## Decision

Actuator runs on `MANAGEMENT_PORT` (default 9091). Docker Compose publishes it only on `127.0.0.1`, and
Prometheus scrapes it inside the compose network (`app:9091`). The API port serves no actuator endpoints.
Health component details are still shown only to callers with an ADMIN token.

## Consequences

- In a real deployment the management port must stay on an internal network (or behind authentication at the
  proxy); the application does not authenticate metric scrapes itself.
- Tests run actuator on the application port (`application-test.yaml`) so MockMvc can reach it.
