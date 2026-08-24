package com.accso.shipmentservive.service;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.repository.ShipmentEventRepository;
import com.accso.shipmentservive.repository.ShipmentEventWriter;
import com.accso.shipmentservive.validation.ParsedShipmentEvent;
import com.accso.shipmentservive.validation.ShipmentEventValidator;
import com.accso.shipmentservive.validation.ValidationResult;
import com.accso.shipmentservive.web.dto.IncomingShipmentEventRequest;
import com.accso.shipmentservive.web.dto.ShipmentEventResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import static com.accso.shipmentservive.util.Strings.blankToNull;

/**
 * Applies the ingest rules for {@code POST /shipment-events}: duplicate detection, validation,
 * and the APPLIED/STALE ruling decision.
 * <p>
 * Duplicate detection is checked against the raw, unvalidated {@code eventId} <em>before</em>
 * full field validation runs - so a resubmission of a known eventId is reported as DUPLICATE even
 * if its payload would otherwise fail validation. This is a deliberate precedence decision where
 * the spec doesn't state an order (documented in NOTES.md).
 */
@Service
public class ShipmentEventIngestService {

    private final ShipmentEventRepository repository;
    private final ShipmentEventWriter writer;
    private final ShipmentQueryService queryService;
    private final ShipmentEventValidator validator;

    public ShipmentEventIngestService(ShipmentEventRepository repository,
                                       ShipmentEventWriter writer,
                                       ShipmentQueryService queryService,
                                       ShipmentEventValidator validator) {
        this.repository = repository;
        this.writer = writer;
        this.queryService = queryService;
        this.validator = validator;
    }

    public ShipmentEventResponse ingest(IncomingShipmentEventRequest request) {
        String rawEventId = blankToNull(request.eventId());

        if (rawEventId != null) {
            Optional<ShipmentEvent> existing = repository.findById(rawEventId);
            if (existing.isPresent()) {
                return duplicateResponse(existing.get(), request);
            }
        }

        ValidationResult result = validator.validate(request);
        if (result instanceof ValidationResult.Invalid invalid) {
            return ShipmentEventResponse.invalid(rawEventId, blankToNull(request.shipmentId()), invalid.reason());
        }
        ParsedShipmentEvent parsed = ((ValidationResult.Valid) result).event();

        ShipmentEvent entity = new ShipmentEvent(parsed.eventId(), parsed.partner(), parsed.shipmentId(),
                parsed.status(), parsed.occurredAt(), parsed.receivedAt(), parsed.location());

        try {
            writer.insert(entity);
        } catch (DataIntegrityViolationException lostRace) {
            // Someone else inserted the exact same eventId between our pre-check above and now -
            // the exact "same event arrives twice at the same instant" scenario the spec calls out.
            ShipmentEvent existing = repository.findById(parsed.eventId()).orElseThrow(() -> lostRace);
            return duplicateResponse(existing, request);
        }

        ShipmentEvent ruling = queryService.rulingEvent(parsed.shipmentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ruling event missing immediately after insert for shipmentId=" + parsed.shipmentId()));
        String outcome = ruling.getEventId().equals(parsed.eventId()) ? "APPLIED" : "STALE";
        return ShipmentEventResponse.stored(outcome, parsed.eventId(), parsed.shipmentId(), ruling.getStatus());
    }

    private ShipmentEventResponse duplicateResponse(ShipmentEvent existing, IncomingShipmentEventRequest request) {
        boolean mismatch = payloadMismatch(existing, request);
        // Echo the *stored* shipmentId/currentStatus, not the incoming payload's - keeps the
        // response internally consistent even if the duplicate's payload disagrees on shipmentId.
        ShipmentEvent ruling = queryService.rulingEvent(existing.getShipmentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ruling event missing for shipmentId=" + existing.getShipmentId() + " despite a stored duplicate"));
        return ShipmentEventResponse.duplicate(existing.getEventId(), existing.getShipmentId(), ruling.getStatus(), mismatch);
    }

    private boolean payloadMismatch(ShipmentEvent existing, IncomingShipmentEventRequest request) {
        return !Objects.equals(existing.getPartner(), blankToNull(request.partner()))
                || !Objects.equals(existing.getShipmentId(), blankToNull(request.shipmentId()))
                || !Objects.equals(existing.getStatus().name(), blankToNull(request.status()))
                || !instantMatches(existing.getOccurredAt(), request.occurredAt())
                || !instantMatches(existing.getReceivedAt(), request.receivedAt())
                || !Objects.equals(existing.getLocation(), blankToNull(request.location()));
    }

    /**
     * Compares by parsed instant value, not raw string, so e.g. "2026-03-10T12:00:00Z" and
     * "2026-03-10T12:00:00.000Z" are treated as the same moment rather than a payload mismatch.
     */
    private boolean instantMatches(Instant stored, String rawIncoming) {
        String value = blankToNull(rawIncoming);
        if (value == null) {
            return false;
        }
        try {
            return stored.equals(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
