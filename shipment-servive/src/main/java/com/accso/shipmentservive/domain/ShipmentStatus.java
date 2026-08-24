package com.accso.shipmentservive.domain;

/**
 * The fixed set of statuses a courier partner may report for a shipment.
 * Values are matched case-sensitively against the incoming webhook payload's
 * {@code status} field; any other value is rejected as INVALID.
 */
public enum ShipmentStatus {
    LABEL_CREATED,
    HANDED_TO_CARRIER,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_EXCEPTION,
    RETURNED
}
