package com.accso.shipmentservive.web.dto;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.domain.ShipmentStatus;

import java.time.Instant;

/**
 * A single element of the {@code GET /shipments/{shipmentId}/events} response array:
 * the event's stored fields, as received.
 */
public record ShipmentEventView(
        String eventId,
        String partner,
        String shipmentId,
        ShipmentStatus status,
        Instant occurredAt,
        Instant receivedAt,
        String location
) {
    public static ShipmentEventView from(ShipmentEvent event) {
        return new ShipmentEventView(
                event.getEventId(),
                event.getPartner(),
                event.getShipmentId(),
                event.getStatus(),
                event.getOccurredAt(),
                event.getReceivedAt(),
                event.getLocation());
    }
}
