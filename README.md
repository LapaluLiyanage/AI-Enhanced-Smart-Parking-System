# AI-Enhanced Smart Parking System

A Spring Boot backend for booking parking slots with real-time availability,
JWT-secured reservations, automatic expiry, dynamic pricing, and an AI layer
(Google Gemini) for demand prediction and natural-language booking.

## Stack

Java 17, Spring Boot 3.2, PostgreSQL, Spring Security + JWT, Google Gemini
API, JUnit 5 + Mockito, Testcontainers.

## Running locally

1. Copy `.env.example` to `.env` and fill in `JWT_SECRET` and `GEMINI_API_KEY`
   (a free key is available at https://aistudio.google.com/apikey).
2. Start Postgres: `docker compose up -d`
3. Export the env vars from `.env` into your shell (or use your IDE's env
   file support), then run: `./mvnw spring-boot:run`
4. The API is available at `http://localhost:8080`. Try `GET /health`.

## Running tests

```bash
docker compose up -d   # required — most test classes use this Postgres instance
./mvnw test
```

One test class (`BookingRepositoryIntegrationTest`) uses Testcontainers to
start its own separate, isolated Postgres container automatically; every
other `@SpringBootTest` class connects to the docker-compose Postgres above.
Docker must be running either way.

## API overview

See [docs/API.md](docs/API.md) for the full endpoint reference and example
requests.

## Architecture notes

- **Booking overlap safety**: `BookingService.createBooking` takes a
  pessimistic write lock (`SELECT ... FOR UPDATE`) on the `ParkingSlot` row
  itself (via `ParkingSlotRepository.findByIdForUpdate`) before checking for
  overlapping bookings, all inside one transaction. The slot row always
  exists — unlike a `Booking` row, which may not exist yet for a slot's
  first-ever reservation — so locking the slot is what actually serializes
  all concurrent booking attempts on it; this is what a concurrency test in
  `BookingServiceConcurrencyTest` verifies by firing 10 concurrent requests
  for the same slot/window and asserting exactly one succeeds.
- **AI as an interface layer**: `AiAssistantService` is an interface;
  `GeminiAssistantService` is the only implementation, calling the Gemini
  REST API directly. Every test that touches AI-dependent code mocks this
  interface, so the test suite never depends on a live API key or network
  access.
- **Hardening ideas not implemented here**: a Postgres `EXCLUDE` constraint
  over a `tstzrange` column (with the `btree_gist` extension) would let the
  database itself guarantee no booking overlap can ever be inserted,
  independent of application code — a natural next step for production
  hardening.

## Known limitations

Honest notes on what's out of scope or left as documented debt for this
portfolio-scale build, rather than silently glossed over:

- **Test database isolation**: only `BookingRepositoryIntegrationTest` uses
  a Testcontainers-provisioned Postgres; the other integration test classes
  share the docker-compose Postgres and use fixed-literal seed data (e.g. a
  location named "Mall Entrance"). Each class passes on a fresh database,
  but re-running the full suite twice without resetting the Postgres volume
  (`docker compose down -v && docker compose up -d`) can hit unique-constraint
  collisions. A cleaner fix is converting every `@SpringBootTest` class to
  the same singleton-Testcontainers pattern.
- **HTTP status codes are not fully idiomatic in a few places**: an unknown
  location currently returns 400 (`IllegalArgumentException`) rather than
  404, and accessing another user's booking returns 400 rather than 403/404.
- **`POST /assistant/book` returns 200, `POST /bookings` returns 201** for
  the same underlying operation (creating a `PENDING` booking) — worth
  aligning.
- **No way to provision an `ADMIN` user or seed locations/slots** through
  the API — registration always creates a `USER`, and locations/slots are
  expected to be seeded directly in the database. Fine for a demo, not for
  a real deployment.
- **Schema managed via `ddl-auto: update`**, not a migration tool like
  Flyway/Liquibase — acceptable here, not production-grade.
- A few controllers (`AdminController`, `LocationController`,
  `PredictionController`) read repositories directly instead of going
  through a service layer, which is a minor departure from the
  `controller → service → repository` layering described above.
