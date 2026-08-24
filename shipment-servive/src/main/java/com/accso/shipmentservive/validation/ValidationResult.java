package com.accso.shipmentservive.validation;

/**
 * Outcome of validating+parsing a raw incoming event payload: either a fully parsed,
 * ready-to-store event, or a human-readable reason it was rejected.
 */
public sealed interface ValidationResult {

    record Valid(ParsedShipmentEvent event) implements ValidationResult {
    }

    record Invalid(String reason) implements ValidationResult {
    }
}
