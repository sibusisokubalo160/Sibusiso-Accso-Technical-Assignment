package com.accso.shipmentservive.validation;

import com.accso.shipmentservive.domain.ShipmentStatus;

import java.time.Instant;

/**
 * A raw incoming event payload after successful validation and parsing: every required
 * field is present and well-formed, ready to be stored as a {@link com.accso.shipmentservive.domain.ShipmentEvent}.
 */
public record ParsedShipmentEvent(
        String eventId,
        String partner,
        String shipmentId,
        ShipmentStatus status,
        Instant occurredAt,
        Instant receivedAt,
        String location
) {
}
