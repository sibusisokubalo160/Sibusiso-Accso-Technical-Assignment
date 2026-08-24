package com.accso.shipmentservive.web.dto;

import com.accso.shipmentservive.domain.ShipmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code POST /shipment-events}. Fields are populated according to the
 * outcome: APPLIED/STALE carry {@code currentStatus}; DUPLICATE additionally carries
 * {@code payloadMismatch}; INVALID swaps {@code currentStatus} out for a human-readable
 * {@code reason} and only echoes whichever identifying fields were present.
 * {@code @JsonInclude(NON_NULL)} keeps irrelevant fields out of each response's JSON entirely,
 * matching the spec's per-outcome shapes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShipmentEventResponse {

    private final String eventId;
    private final String shipmentId;
    private final String outcome;
    private final ShipmentStatus currentStatus;
    private final Boolean payloadMismatch;
    private final String reason;

    private ShipmentEventResponse(String eventId, String shipmentId, String outcome,
                                   ShipmentStatus currentStatus, Boolean payloadMismatch, String reason) {
        this.eventId = eventId;
        this.shipmentId = shipmentId;
        this.outcome = outcome;
        this.currentStatus = currentStatus;
        this.payloadMismatch = payloadMismatch;
        this.reason = reason;
    }

    public static ShipmentEventResponse stored(String outcome, String eventId, String shipmentId,
                                                ShipmentStatus currentStatus) {
        return new ShipmentEventResponse(eventId, shipmentId, outcome, currentStatus, null, null);
    }

    public static ShipmentEventResponse duplicate(String eventId, String shipmentId,
                                                   ShipmentStatus currentStatus, boolean payloadMismatch) {
        return new ShipmentEventResponse(eventId, shipmentId, "DUPLICATE", currentStatus, payloadMismatch, null);
    }

    public static ShipmentEventResponse invalid(String eventId, String shipmentId, String reason) {
        return new ShipmentEventResponse(eventId, shipmentId, "INVALID", null, null, reason);
    }

    public String getEventId() {
        return eventId;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public String getOutcome() {
        return outcome;
    }

    public ShipmentStatus getCurrentStatus() {
        return currentStatus;
    }

    public Boolean getPayloadMismatch() {
        return payloadMismatch;
    }

    public String getReason() {
        return reason;
    }
}
