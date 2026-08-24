package com.accso.shipmentservive.domain;

import java.util.Comparator;

/**
 * The single source of truth for the shipment event total order defined by the spec:
 * ruling event = latest {@code occurredAt}; ties broken by later {@code receivedAt};
 * remaining ties broken by the greater {@code eventId} (plain string comparison).
 * <p>
 * Because {@code eventId} is unique per event, this order is total - every shipment has
 * exactly one maximum (ruling) event. The same comparator backs both:
 * <ul>
 *     <li>{@code GET /shipments/{id}/events} - sort ascending, return as-is</li>
 *     <li>the ruling event - the maximum element of that same ordering</li>
 * </ul>
 * so the two endpoints can never disagree about ordering.
 */
public final class ShipmentEventOrder {

    /**
     * Ascending order: occurredAt, then receivedAt, then eventId (String natural/"plain" ordering).
     * The last element of a list sorted with this comparator is always the ruling event.
     */
    public static final Comparator<ShipmentEvent> ASCENDING =
            Comparator.comparing(ShipmentEvent::getOccurredAt)
                    .thenComparing(ShipmentEvent::getReceivedAt)
                    .thenComparing(ShipmentEvent::getEventId);

    private ShipmentEventOrder() {
    }
}
