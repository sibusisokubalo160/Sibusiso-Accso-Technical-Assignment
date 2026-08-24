package com.accso.shipmentservive.web.dto;

/**
 * Raw shape of the inbound webhook payload, exactly as the spec's fixed schema defines it.
 * <p>
 * {@code status}, {@code occurredAt} and {@code receivedAt} are deliberately kept as {@code String}
 * rather than bound directly to {@code ShipmentStatus}/{@code Instant}. Binding them to those types
 * would make Jackson fail the whole request with a generic, uncontrolled 400 the moment any one of
 * them is missing or malformed - before our own validation ever runs. Keeping them as strings lets
 * {@link com.accso.shipmentservive.validation.ShipmentEventValidator} parse them itself and produce
 * the spec's exact INVALID response shape (with a human-readable reason and the identifying fields
 * that were present).
 */
public record IncomingShipmentEventRequest(
        String eventId,
        String partner,
        String shipmentId,
        String status,
        String occurredAt,
        String receivedAt,
        String location
) {
}
