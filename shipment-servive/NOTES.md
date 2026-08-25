# NOTES

## Design decisions where the spec was open to interpretation

**No cached/denormalized "current state" table.** `currentStatus`, `rulingEventId`,
`statusOccurredAt`, and `eventsStored` are always computed fresh from the stored events
(`ShipmentQueryService`), never cached in a separate row. This was a deliberate simplicity choice:
with only one table, there's nothing that can drift out of sync with the events it's supposed to
summarize, and it sidesteps an entire class of cache-invalidation bugs. The trade-off is discussed
below under limitations.

**Duplicate-check precedence over field validation.** If an incoming `eventId` matches an already
stored event, the response is `DUPLICATE` even if the rest of that payload would otherwise fail
validation (missing field, bad status, etc.). The spec doesn't state an order between the two
checks; this seemed the more useful behavior for a retry storm (report "already handled" rather
than "invalid" for a request whose only real problem is that it's a stale/mangled retry).

**`DUPLICATE` response echoes the *stored* event's `shipmentId`/`currentStatus`, not the incoming
payload's**, when they disagree. Rationale: the response's `currentStatus` necessarily describes
whatever shipment is actually in the system under that `eventId`; echoing the incoming payload's
(possibly different) `shipmentId` alongside a `currentStatus` computed for a different shipment
would make the response internally inconsistent.

**`payloadMismatch` uses parsed/semantic equality for timestamps, not raw string equality.**
`"2026-03-10T12:00:00Z"` and `"2026-03-10T12:00:00.000Z"` denote the same instant and are not
treated as a mismatch, even though they're different strings.

**`GET /shipments/{id}/events` 404s for an unknown shipment.** The spec states this explicitly
only for `GET /shipments/{id}`; decided to do the same for the history endpoint for consistency
rather than returning an empty array.

**`status` matching is case-sensitive / exact-literal** against the seven fixed enum values,
and eventId comparisons use Java's `String.compareTo` (UTF-16 ordinal), matching "plain string
comparison" literally rather than relying on the database's collation for `ORDER BY`.

**No extra temporal sanity checks.** E.g. `receivedAt` earlier than `occurredAt`, or timestamps far
in the future, are accepted as long as they parse as valid ISO-8601 instants - the spec only asks
for "required fields missing, or cannot be parsed" as grounds for `INVALID`.

**`ddl-auto=update`** is used instead of a real migration tool (Flyway/Liquibase) to keep the
exercise's footprint small; see "what's next" below.

## Known limitations

- **Concurrent, *different*-eventId submissions for the same shipment**: each insert is followed
  by a fresh read of the ruling event within that request's own transaction. Under
  READ_COMMITTED, two such requests racing for the same shipment can each see a slightly different
  snapshot of "what's ruling right now" depending on commit interleaving - so which one's response
  says `APPLIED` vs `STALE` isn't fully linearizable. This never causes data loss or corruption:
  every event is stored, and any subsequent `GET` recomputes from all committed rows and is always
  correct. The spec's own concurrency requirement is specifically about the *same* event arriving
  twice at the same instant (i.e. same `eventId`), which is handled deterministically - see
  `ShipmentEventWriter`'s Javadoc for how.
- **In-memory lock striping / distributed locking was deliberately not added.** Correctness for
  the same-eventId race relies entirely on the database's primary-key constraint, which holds even
  across multiple service instances. No multi-instance testing was actually done, though.
- **The H2 mem-database registry is process-wide**, so test isolation relies on
  `${random.uuid}` in the test datasource URL (see `src/test/resources/application.properties`)
  rather than on separate JVMs; this is noted in case someone adds a test config that reuses the
  exact same property set as an existing one and unexpectedly shares state.
- The `location` field, when submitted as an empty/blank string, is treated the same as "not
  provided" (stored as `null`), rather than as a distinct empty-string value.

## Interesting surprise along the way

This was built against **Spring Boot 4.1.1**, which turned out to have reorganized both its
starter artifacts (the scaffold already had e.g. `spring-boot-starter-webmvc-test` instead of the
classic `spring-boot-starter-test`) and, more surprisingly, moved to **Jackson 3**
(`tools.jackson.databind.*` instead of `com.fasterxml.jackson.databind.*` for `ObjectMapper`/
`JsonNode`; `jackson-annotations` stayed on the old `com.fasterxml` package). Test-support classes
also moved packages (e.g. `@DataJpaTest` is now `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
`TestRestTemplate` is now `org.springframework.boot.resttestclient.TestRestTemplate` and needs
`spring-boot-starter-restclient-test` explicitly - it's no longer pulled in automatically by the
web test starter). None of this affects the design; it just cost extra investigation time that
wouldn't be needed on Boot 2.x/3.x.

## What I'd tackle next

- Flyway/Liquibase migrations instead of `ddl-auto=update`.
- A real distributed-safe mechanism (e.g. a `SELECT ... FOR UPDATE` on a per-shipment row, or an
  outbox/queue ahead of ingestion) if outcome labeling for concurrent different-event submissions
  ever needs to be linearizable, or if this needs to run as more than one instance against
  contended shipments.
- Structured logging/metrics per outcome (APPLIED/STALE/DUPLICATE/INVALID counts) - useful for the
  "incident response" use case called out in the prompt.
- Pagination for `GET /shipments/{id}/events` if per-shipment event volume ever grows large enough
  for the current "load them all, sort in memory" approach to matter.
- OpenAPI/Swagger documentation of the three endpoints.

## Time spent

This was built in a single focused AI-assisted session (Claude Code, with me steering the design,
reviewing every file, and driving the manual verification), per the assignment's explicit allowance
to use AI tooling. Given the number of edge cases covered end-to-end (28 automated tests including
a real concurrent-request test and a real process-restart test) plus the unplanned time spent
diagnosing Spring Boot 4.1's package/artifact reorganization (see above), an engineer building this
by hand would likely land at the higher end of, or modestly over, the suggested 3-5 hour window.
