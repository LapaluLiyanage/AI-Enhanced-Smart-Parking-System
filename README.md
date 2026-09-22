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
docker compose up -d   # only needed for the concurrency test
./mvnw test
```

Testcontainers will automatically start its own Postgres container for the
repository integration tests — Docker must be running.

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
