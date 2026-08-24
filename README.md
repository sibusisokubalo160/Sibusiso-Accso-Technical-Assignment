# shipment-servive

A backend service that ingests shipment status events from courier partners, resolves duplicates
/ out-of-order / conflicting updates deterministically, and exposes a reliable current-status view
and ordered history per shipment.

Built for the Accso technical interview assignment.

## Requirements

- Java 21
- No local Gradle install needed - the repo includes the Gradle wrapper (`gradlew`/`gradlew.bat`).

## Build & run

```bash
./gradlew bootRun
```

(Windows: `gradlew.bat bootRun`)

The service starts on `http://localhost:8080` and persists data to a file-based H2 database under
`./data/` (created automatically on first run, relative to the working directory). Stop and
restart the process and previously stored events are still there - this is deliberately not an
in-memory-only store, per the spec's persistence requirement.

An H2 web console for local inspection is available at `http://localhost:8080/h2-console`. The
login page pre-fills its own default (`jdbc:h2:~/test`), which is **not** this project's database
and will fail with a "Database ... not found" error - replace the **JDBC URL** field with
`jdbc:h2:file:./data/shipment-servive`, user `sa`, blank password, then Connect.

## Run the tests

```bash
./gradlew test
```

Single command, full suite: unit tests, `@DataJpaTest` repository tests, `@SpringBootTest`/MockMvc
end-to-end tests covering every ingest outcome, a concurrency test that fires several identical
requests at a real running server simultaneously, and a restart-survival test. See
`build/reports/tests/test/index.html` for the HTML report after running.

## API

### `POST /shipment-events`

Ingests one courier webhook event. Always returns one of four outcomes:

| Outcome     | HTTP | Meaning |
|-------------|------|---------|
| `APPLIED`   | 201  | Stored, and now the ruling (current) event for the shipment. |
| `STALE`     | 201  | Stored, but an already-stored event still rules; current status unchanged. |
| `DUPLICATE` | 200  | Not stored - `eventId` already seen. `payloadMismatch` tells you whether the retry's payload disagrees with the original. |
| `INVALID`   | 400  | Not stored - a required field was missing/unparseable, or `status` isn't a recognized value. `reason` explains why. |

Example request:
```json
{
  "eventId": "evt-123",
  "partner": "dhl",
  "shipmentId": "ship-456",
  "status": "IN_TRANSIT",
  "occurredAt": "2026-03-10T12:00:00Z",
  "receivedAt": "2026-03-10T12:00:05Z",
  "location": "Amsterdam"
}
```

### `GET /shipments/{shipmentId}`

Current view: `currentStatus`, `statusOccurredAt` (the ruling event's `occurredAt`),
`rulingEventId`, `eventsStored`. 404 if the shipment has no stored events.

### `GET /shipments/{shipmentId}/events`

Full stored history, ascending by `occurredAt`, then `receivedAt`, then `eventId`. The last
element is always the ruling event. 404 if the shipment has no stored events.

## How the ordering/conflict rules are implemented

Everything about "what's the current status" is computed fresh from the stored events on every
request - there's no separate cached summary table to go stale. The single source of truth for
ordering is `ShipmentEventOrder` (`src/main/java/com/accso/shipmentservive/domain/ShipmentEventOrder.java`):
ascending by `occurredAt`, then `receivedAt`, then `eventId` (plain string comparison). The ruling
event is simply the maximum element under that same comparator - used both to answer the GET
endpoints and to decide APPLIED vs STALE right after an insert.

Concurrent submission of the exact same `eventId` is guarded at the database level: `eventId` is
the assigned primary key, and the insert path (`ShipmentEventWriter`) forces an INSERT (not an
upsert) so a colliding write throws instead of silently overwriting - see the Javadoc on that
class and on `ShipmentEventIngestService` for the full reasoning.

## Project layout

```
domain/        entity, status enum, the shared ordering comparator
repository/    Spring Data repository + the insert-only writer
validation/    raw payload -> parsed event, or a human-readable rejection reason
service/       ingest orchestration (rules) and query derivation (current view/history)
web/           controllers, DTOs, error mapping
exception/     ShipmentNotFoundException -> 404
```
