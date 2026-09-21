# AI-Enhanced Smart Parking System — Design Spec

Date: 2026-09-22

## Purpose

A Spring Boot backend for booking parking slots with real-time availability,
reservations, automatic expiry, and an AI layer for demand prediction and
natural-language booking. Built as an interview-ready portfolio project
demonstrating classic backend engineering (concurrency, state machines,
scheduling, layered architecture) combined with a practical, testable LLM
integration.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Database | PostgreSQL (via Docker Compose) | Production-style, works well with JPA/Hibernate |
| LLM provider | Google Gemini (`gemini-1.5-flash` / `gemini-2.0-flash`) | Genuinely free tier, supports function calling/structured output |
| AI parsing approach | Gemini function calling (tool use) | Cleanest mapping to "LLM as translation layer"; model returns structured args directly |
| Booking overlap concurrency | Pessimistic lock (`SELECT ... FOR UPDATE`) + app-level overlap check in a transaction | Simple, portable across DBs, easy to unit-test, strong concurrency talking point |
| Auth | Spring Security + JWT, roles `USER` / `ADMIN` | Matches doc's stack section; common interview topic |
| Test DB strategy | Testcontainers with real Postgres | Behavior-identical to production DB; catches Postgres-specific issues |
| Containerization | `docker-compose.yml` for Postgres only (app run via Maven) | Zero manual DB setup, app still runs locally for fast iteration |
| Commit strategy | One commit per numbered stage, pushed to GitHub after each stage | Demonstrates incremental, reviewable development history |

## Architecture

Standard layered Spring Boot application:

```
controller → service → repository
```

Package layout:

- `controller` — REST controllers (thin, delegate to services)
- `service` — business logic (`BookingService`, `PricingService`, `AnomalyDetectionService`)
- `repository` — Spring Data JPA repositories
- `entity` — JPA entities
- `ai` — `AiAssistantService` interface + `GeminiAssistantService` implementation, kept behind an interface so it is mockable in tests without needing a live API key
- `scheduler` — `@Scheduled` reservation-expiry job
- `security` — JWT filter, `UserDetailsService`, Spring Security config
- `exception` — domain exceptions + `@RestControllerAdvice` global handler
- `dto` — request/response DTOs

Configuration (`application.yml`) reads secrets from environment variables:
`GEMINI_API_KEY`, `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`. A
`docker-compose.yml` at the repo root runs Postgres for local development and
for Testcontainers-based integration tests (Testcontainers manages its own
container independently of this compose file).

## Data Model

- **User**: id, email (unique), passwordHash, role (`USER` | `ADMIN`)
- **Location**: id, name, address, totalSlots
- **ParkingSlot**: id, location (FK), slotNumber, status (`AVAILABLE` | `RESERVED` | `OCCUPIED`)
- **Booking**: id, user (FK), slot (FK), startTime, endTime, status, createdAt, confirmedAt, totalCost

### Booking status lifecycle

```
PENDING --confirm--> CONFIRMED --checkin--> ACTIVE --checkout--> COMPLETED
   |                     |
   +--(expiry job)--> EXPIRED
   |
   +--cancel--> CANCELLED
```

- `PENDING`: just reserved, awaiting confirmation within an expiry window (default 10 minutes).
- `CONFIRMED`: confirmed by the user, slot remains `RESERVED` until check-in.
- `ACTIVE`: user has checked in; slot status becomes `OCCUPIED`.
- `COMPLETED`: user has checked out; cost is computed here; slot returns to `AVAILABLE`.
- `EXPIRED`: set by the scheduled job if not confirmed within the expiry window; slot returns to `AVAILABLE`.
- `CANCELLED`: user-cancelled before `ACTIVE`; slot returns to `AVAILABLE`.

### Overlap rule

For a given slot, no two bookings with status in `{PENDING, CONFIRMED, ACTIVE}`
may have overlapping `[startTime, endTime)` ranges. Enforced in
`BookingService.createBooking` via a pessimistic lock query over that slot's
active bookings, followed by an in-transaction overlap check, followed by
insert — all within a single `@Transactional` method so the lock is held for
the duration of the check-and-insert.

Pricing rules are not persisted as a separate entity; `PricingService`
computes cost on demand from a base hourly rate and a surge multiplier.

## API Surface

### Auth
- `POST /auth/register` — `{email, password}` → creates `USER`
- `POST /auth/login` — `{email, password}` → returns JWT

### Locations & Slots
- `GET /locations`
- `GET /locations/{id}/slots?available=true`

### Bookings
- `POST /bookings` — `{slotId, startTime, endTime}` → creates `PENDING` booking
- `POST /bookings/{id}/confirm` → `PENDING` → `CONFIRMED`
- `POST /bookings/{id}/checkin` → `CONFIRMED` → `ACTIVE`, slot → `OCCUPIED`
- `POST /bookings/{id}/checkout` → `ACTIVE` → `COMPLETED`, computes cost via `PricingService`, slot → `AVAILABLE`
- `DELETE /bookings/{id}` → cancel (only if not yet `ACTIVE`)

### AI
- `GET /predictions/{locationId}` — builds a prompt from historical hourly
  booking counts for that location, asks Gemini for a predicted occupancy %,
  returns `{locationId, hour, predictedOccupancyPct}`. Predictions may be
  cached briefly (e.g. in-memory, a few minutes TTL) so `PricingService`
  doesn't trigger a live LLM call on every checkout.
- `POST /assistant/book` — `{message}` → Gemini function-call (tool:
  `create_booking(locationName, startTime, durationMinutes)`) parses intent →
  resolves `locationName` to a `locationId` → delegates to the same
  `BookingService.createBooking(...)` used by `POST /bookings` (no duplicated
  business logic).

### Admin
- `GET /admin/locations/{id}/occupancy` (ADMIN only) — aggregate JPQL query
  returning current occupancy % and slot-status counts.

## Pricing

`PricingService.calculateCost(booking)`:

```
cost = durationHours * baseHourlyRate * surgeMultiplier
```

`surgeMultiplier` is derived from the cached prediction for that
location/hour: high predicted occupancy (e.g. >75%) → 1.5x, low (e.g. <30%)
→ 0.8x, otherwise 1.0x. Computed at checkout time using the
currently-cached prediction — deterministic and testable without requiring a
live LLM call inside the checkout transaction.

## Anomaly Detection

`AnomalyDetectionService`, rule-based (no ML): before creating a booking,
check whether the same user has created more than N bookings (e.g. 5) in the
last M minutes (e.g. 5); if so, reject with a 429/409-style domain exception.
Cheap to implement, framed as a "responsible engineering" talking point.

## Error Handling

Domain exceptions: `SlotUnavailableException`, `OverlappingBookingException`,
`BookingNotFoundException`, `InvalidBookingStateException`,
`AnomalyDetectedException`. A `@RestControllerAdvice` maps each to an
appropriate HTTP status (404 not found, 409 conflict, 400 bad request, 429
too many requests) with a consistent JSON error body:
`{timestamp, status, error, message, path}`.

## Testing Strategy

- Unit tests (JUnit + Mockito) for `BookingService`, `PricingService`,
  `AnomalyDetectionService`, and the AI parsing logic, with
  `AiAssistantService` mocked so tests don't depend on network/API keys.
- One concurrency test: multiple threads (`ExecutorService` /
  `CompletableFuture`) concurrently call `createBooking` for the same slot
  with overlapping time windows; assert exactly one succeeds and the rest
  throw `OverlappingBookingException`.
- Repository-level integration tests using Testcontainers with a real
  Postgres container, verifying the overlap query and pessimistic lock
  behavior against actual Postgres semantics.

## Staged Implementation & Commit Plan

Each stage is independently buildable and gets its own commit, pushed to
GitHub after the stage is verified (compiles, relevant tests pass).

1. **Project skeleton** — Spring Boot (Web, JPA, Security, Validation) Maven
   project, `docker-compose.yml` (Postgres), `application.yml`, package
   structure, health-check endpoint.
2. **Auth** — `User` entity, JWT filter, register/login, Spring Security
   config with roles.
3. **Location & ParkingSlot** entities + availability endpoints.
4. **Booking core** — `Booking` entity, overlap-check + pessimistic locking
   in `BookingService`, create/cancel endpoints, global exception handler.
5. **Booking lifecycle** — confirm/checkin/checkout endpoints, state machine
   transitions.
6. **Scheduled expiry job**.
7. **Pricing** — `PricingService` with base rate + surge multiplier, wired
   into checkout.
8. **AI: demand prediction** — `AiAssistantService` interface +
   `GeminiAssistantService`, `GET /predictions/{locationId}`.
9. **AI: NL booking assistant** — Gemini function calling,
   `POST /assistant/book`.
10. **Admin occupancy stats + anomaly detection**.
11. **Tests** — unit tests (Mockito) for services, concurrency test,
    Testcontainers repository tests.
12. **README + API docs** tying it all together (setup instructions, env
    vars needed, example requests).

## Out of Scope

- Frontend/UI (backend-only project).
- Real ML model training for demand prediction (LLM-based prediction is an
  intentional, explained trade-off per the original doc's "Option A" note).
- Postgres `EXCLUDE` constraint / `tstzrange` hardening (mentioned in README
  as a "how you'd harden this further" note, not implemented).
- Payment processing (billing is cost calculation only, no real payment
  gateway integration).
