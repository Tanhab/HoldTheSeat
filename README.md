# HoldTheSeat

An event-ticketing backend built as three independently-deployed Spring Boot services
that share no database and coordinate only over Kafka. You book seats for a show; a
choreographed saga holds them, charges you, and confirms — and when any step fails, the
system unwinds the partial work across service boundaries with no distributed
transaction anywhere.

> **Status: Phase 0 — in progress.** The booking service scaffold and local stack are
> up. Nothing is booked yet. See [Roadmap](#roadmap).

## The two invariants

Everything here exists to protect two properties:

1. **Never double-book a seat.** Under a flood of simultaneous requests for seat A12,
   exactly one wins.
2. **Failure leaves no trace.** A declined payment returns the seat map to *exactly* its
   starting availability — not approximately, exactly.

## Architecture

Three services, each a separate Spring Boot app with its own database. They never touch
each other's tables; they only exchange events over Kafka.

| Service | Owns | Talks to |
|---|---|---|
| **booking** | `bookings` and the booking lifecycle (`PENDING → CONFIRMED / CANCELLED / EXPIRED`). The only service a customer calls. | Kafka |
| **seat** | `shows`, `seats` (durable `AVAILABLE`/`SOLD`), and transient hold state in Redis. | Kafka + Redis |
| **payment** | `payments` and a mock card gateway that declines on demand. | Kafka |

There is **no central saga orchestrator**. Each service reacts to events and emits its
own, so the global saga state is implicit in the event stream — choreography, not
orchestration. Every event is keyed by `booking_id` so all events for one booking land
on the same partition and are never reordered relative to each other.

Happy path: `BookingRequested → SeatsHeld → PaymentAuthorized → BookingConfirmed`.
Failure paths: `SeatsRejected`, `PaymentFailed`, `BookingCancelled`, `SeatsReleased`,
`BookingExpired`.

## Stack

Java 25 (LTS) · Spring Boot 4.1 (Spring Framework 7) · Maven multi-module · PostgreSQL 18
with `JdbcClient` and hand-written SQL (no ORM) · Flyway · Apache Kafka 4.1 in KRaft mode
· Redis 8 · Docker Compose · Testcontainers · Terraform + AWS (later phases).

## Running locally

Requires Docker and JDK 25.

```bash
docker compose up -d                          # postgres + kafka + redis
./mvnw clean install -DskipTests
./mvnw -pl booking-service spring-boot:run    # http://localhost:8081
```

Health check: `GET /actuator/health`.

```bash
./mvnw -pl booking-service test               # integration tests via Testcontainers
docker compose down                           # stop; add -v to wipe data
```

## Roadmap

| Phase | | |
|---|---|---|
| 0 | Booking service, schema, auth, local stack | in progress |
| 1 | Seat map and the atomic no-double-book hold (Redis + Lua) | |
| 2 | Kafka choreography, transactional outbox, idempotent consumers | |
| 3 | Failure and compensation — the saga unwinds | |
| 4 | Expiry, the sweeper, and the TTL split | |
| 5 | Live seat map, booking timeline, correlation ids | |
| 6 | Containerize and deploy (Compose + Terraform/AWS) | |
| 7 | Benchmarks proving both invariants | |
