package com.accso.shipmentservive.web;

import com.accso.shipmentservive.service.ShipmentEventIngestService;
import com.accso.shipmentservive.web.dto.IncomingShipmentEventRequest;
import com.accso.shipmentservive.web.dto.ShipmentEventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShipmentEventController {

    private final ShipmentEventIngestService ingestService;

    public ShipmentEventController(ShipmentEventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/shipment-events")
    public ResponseEntity<ShipmentEventResponse> ingest(@RequestBody IncomingShipmentEventRequest request) {
        ShipmentEventResponse response = ingestService.ingest(request);
        HttpStatus status = switch (response.getOutcome()) {
            case "APPLIED", "STALE" -> HttpStatus.CREATED;
            case "DUPLICATE" -> HttpStatus.OK;
            case "INVALID" -> HttpStatus.BAD_REQUEST;
            default -> throw new IllegalStateException("Unknown outcome: " + response.getOutcome());
        };
        return ResponseEntity.status(status).body(response);
    }
}
