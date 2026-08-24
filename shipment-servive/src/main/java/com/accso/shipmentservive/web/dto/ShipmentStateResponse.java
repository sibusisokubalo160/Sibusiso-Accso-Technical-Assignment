package com.accso.shipmentservive.web.dto;

import com.accso.shipmentservive.domain.ShipmentStatus;

import java.time.Instant;

/**
 * Response body for {@code GET /shipments/{shipmentId}}.
 */
public record ShipmentStateResponse(
        String shipmentId,
        ShipmentStatus currentStatus,
        Instant statusOccurredAt,
        String rulingEventId,
        long eventsStored
) {
}
