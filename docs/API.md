# API Reference

Base URL: `http://localhost:8080`

All endpoints except `/health` and `/auth/**` require an
`Authorization: Bearer <jwt>` header obtained from `/auth/login`.
`/admin/**` additionally requires the `ADMIN` role.

## Auth

### `POST /auth/register`
```json
{ "email": "user@example.com", "password": "password123" }
```
→ `201 Created`

### `POST /auth/login`
```json
{ "email": "user@example.com", "password": "password123" }
```
→ `200 OK` `{ "token": "<jwt>" }`

## Locations & Slots

### `GET /locations`
→ `[{ "id": 1, "name": "Mall Parking", "address": "...", "totalSlots": 20 }]`

### `GET /locations/{id}/slots?available=true`
→ `[{ "id": 1, "locationId": 1, "slotNumber": 1, "status": "AVAILABLE" }]`

## Bookings

### `POST /bookings`
```json
{ "slotId": 1, "startTime": "2026-09-23T15:00:00Z", "endTime": "2026-09-23T17:00:00Z" }
```
→ `201 Created`, booking in `PENDING` status. `409 Conflict` if the slot is
occupied or the window overlaps an existing active booking.

### `POST /bookings/{id}/confirm`
`PENDING` → `CONFIRMED`. Must happen before the pending-expiry window (10
minutes by default) elapses, or the scheduled job marks it `EXPIRED`.

### `POST /bookings/{id}/checkin`
`CONFIRMED` → `ACTIVE`; slot becomes `OCCUPIED`.

### `POST /bookings/{id}/checkout`
`ACTIVE` → `COMPLETED`; computes `totalCost` via `PricingService` (base rate
× duration × occupancy-based surge/discount multiplier); slot becomes
`AVAILABLE`.

### `DELETE /bookings/{id}`
Cancels a booking not yet `ACTIVE`.

## AI

### `GET /predictions/{locationId}`
→ `{ "locationId": 1, "predictedOccupancyPct": 0.82, "reasoning": "..." }`
Cached for 5 minutes per location.

### `POST /assistant/book`
```json
{ "message": "book me a spot near the mall entrance for 2 hours starting at 3pm" }
```
Parses intent via Gemini function calling, resolves the closest matching
location, and creates a `PENDING` booking exactly as `POST /bookings` would.

## Admin

### `GET /admin/locations/{id}/occupancy` (ADMIN only)
→ `{ "locationId": 1, "totalSlots": 20, "available": 12, "reserved": 5, "occupied": 3, "occupancyPct": 0.4 }`
