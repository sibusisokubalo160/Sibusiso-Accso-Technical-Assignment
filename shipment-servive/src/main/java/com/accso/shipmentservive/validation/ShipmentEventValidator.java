package com.accso.shipmentservive.validation;

import com.accso.shipmentservive.domain.ShipmentStatus;
import com.accso.shipmentservive.web.dto.IncomingShipmentEventRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import static com.accso.shipmentservive.util.Strings.blankToNull;

/**
 * Validates and parses a raw incoming event payload per the spec's rules: every field except
 * {@code location} is required, {@code status} must be one of the fixed enum values, and the two
 * timestamps must be parseable ISO-8601 instants. Reports the first failure found, in field order.
 */
@Component
public class ShipmentEventValidator {

    public ValidationResult validate(IncomingShipmentEventRequest request) {
        String eventId = blankToNull(request.eventId());
        if (eventId == null) {
            return new ValidationResult.Invalid("eventId is required");
        }

        String partner = blankToNull(request.partner());
        if (partner == null) {
            return new ValidationResult.Invalid("partner is required");
        }

        String shipmentId = blankToNull(request.shipmentId());
        if (shipmentId == null) {
            return new ValidationResult.Invalid("shipmentId is required");
        }

        String statusRaw = blankToNull(request.status());
        if (statusRaw == null) {
            return new ValidationResult.Invalid("status is required");
        }
        ShipmentStatus status;
        try {
            status = ShipmentStatus.valueOf(statusRaw);
        } catch (IllegalArgumentException e) {
            return new ValidationResult.Invalid("Unknown status value: " + statusRaw);
        }

        String occurredAtRaw = blankToNull(request.occurredAt());
        if (occurredAtRaw == null) {
            return new ValidationResult.Invalid("occurredAt is required");
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(occurredAtRaw);
        } catch (DateTimeParseException e) {
            return new ValidationResult.Invalid("occurredAt is not a valid ISO-8601 UTC timestamp: " + occurredAtRaw);
        }

        String receivedAtRaw = blankToNull(request.receivedAt());
        if (receivedAtRaw == null) {
            return new ValidationResult.Invalid("receivedAt is required");
        }
        Instant receivedAt;
        try {
            receivedAt = Instant.parse(receivedAtRaw);
        } catch (DateTimeParseException e) {
            return new ValidationResult.Invalid("receivedAt is not a valid ISO-8601 UTC timestamp: " + receivedAtRaw);
        }

        String location = blankToNull(request.location());

        return new ValidationResult.Valid(
                new ParsedShipmentEvent(eventId, partner, shipmentId, status, occurredAt, receivedAt, location));
    }
}
