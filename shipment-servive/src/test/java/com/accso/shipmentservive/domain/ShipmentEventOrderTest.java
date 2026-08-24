package com.accso.shipmentservive.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipmentEventOrderTest {

    private static ShipmentEvent event(String eventId, Instant occurredAt, Instant receivedAt) {
        return new ShipmentEvent(eventId, "dhl", "ship-1", ShipmentStatus.IN_TRANSIT, occurredAt, receivedAt, null);
    }

    @Test
    void ordersByOccurredAtFirst() {
        ShipmentEvent earlier = event("evt-1", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        ShipmentEvent later = event("evt-2", Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        List<ShipmentEvent> sorted = List.of(later, earlier).stream()
                .sorted(ShipmentEventOrder.ASCENDING)
                .toList();

        assertEquals("evt-1", sorted.get(0).getEventId());
        assertEquals("evt-2", sorted.get(1).getEventId());
    }

    @Test
    void tieOnOccurredAt_brokenByReceivedAt() {
        Instant sameOccurredAt = Instant.parse("2026-01-01T00:00:00Z");
        ShipmentEvent earlierReceived = event("evt-1", sameOccurredAt, Instant.parse("2026-01-01T00:00:00Z"));
        ShipmentEvent laterReceived = event("evt-2", sameOccurredAt, Instant.parse("2026-01-01T00:00:05Z"));

        List<ShipmentEvent> sorted = List.of(laterReceived, earlierReceived).stream()
                .sorted(ShipmentEventOrder.ASCENDING)
                .toList();

        assertEquals("evt-1", sorted.get(0).getEventId());
        assertEquals("evt-2", sorted.get(1).getEventId());
    }

    @Test
    void tieOnOccurredAtAndReceivedAt_brokenByGreaterEventId() {
        Instant sameOccurredAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant sameReceivedAt = Instant.parse("2026-01-01T00:00:05Z");
        ShipmentEvent lowerId = event("evt-100", sameOccurredAt, sameReceivedAt);
        ShipmentEvent higherId = event("evt-200", sameOccurredAt, sameReceivedAt);

        List<ShipmentEvent> sorted = List.of(higherId, lowerId).stream()
                .sorted(ShipmentEventOrder.ASCENDING)
                .toList();

        assertEquals("evt-100", sorted.get(0).getEventId());
        assertEquals("evt-200", sorted.get(1).getEventId());
    }

    @Test
    void eventIdTieBreakUsesPlainStringComparison_not_numeric_or_length() {
        // "evt-9" > "evt-10" under plain string comparison ('9' > '1'), even though it is
        // numerically smaller and shorter - this pins down the "plain string comparison" rule.
        Instant sameOccurredAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant sameReceivedAt = Instant.parse("2026-01-01T00:00:05Z");
        ShipmentEvent evt9 = event("evt-9", sameOccurredAt, sameReceivedAt);
        ShipmentEvent evt10 = event("evt-10", sameOccurredAt, sameReceivedAt);

        List<ShipmentEvent> sorted = List.of(evt9, evt10).stream()
                .sorted(ShipmentEventOrder.ASCENDING)
                .toList();

        assertEquals("evt-10", sorted.get(0).getEventId());
        assertEquals("evt-9", sorted.get(1).getEventId());
    }

    @Test
    void ascendingListLastElement_equalsMaxByComparator() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        List<ShipmentEvent> events = List.of(
                event("evt-3", t0.plusSeconds(30), t0),
                event("evt-1", t0, t0),
                event("evt-2", t0.plusSeconds(10), t0)
        );

        ShipmentEvent lastOfAscending = events.stream().sorted(ShipmentEventOrder.ASCENDING).toList()
                .get(events.size() - 1);
        ShipmentEvent max = events.stream().max(ShipmentEventOrder.ASCENDING).orElseThrow();

        assertEquals(max.getEventId(), lastOfAscending.getEventId());
        assertEquals("evt-3", max.getEventId());
    }
}
