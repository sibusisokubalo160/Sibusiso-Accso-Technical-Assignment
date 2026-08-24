package com.accso.shipmentservive.exception;

/**
 * Thrown when a shipment is queried (current-state or history) but has no stored events -
 * i.e. it is unknown to the system. Mapped to HTTP 404 by
 * {@link com.accso.shipmentservive.web.GlobalExceptionHandler}.
 */
public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(String shipmentId) {
        super("Unknown shipmentId: " + shipmentId);
    }
}
