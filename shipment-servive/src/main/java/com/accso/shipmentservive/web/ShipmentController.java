package com.accso.shipmentservive.web;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.exception.ShipmentNotFoundException;
import com.accso.shipmentservive.service.ShipmentQueryService;
import com.accso.shipmentservive.web.dto.ShipmentEventView;
import com.accso.shipmentservive.web.dto.ShipmentStateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentQueryService queryService;

    public ShipmentController(ShipmentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{shipmentId}")
    public ShipmentStateResponse getState(@PathVariable String shipmentId) {
        long eventsStored = queryService.countStored(shipmentId);
        if (eventsStored == 0) {
            throw new ShipmentNotFoundException(shipmentId);
        }
        ShipmentEvent ruling = queryService.rulingEvent(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
        return new ShipmentStateResponse(
                shipmentId, ruling.getStatus(), ruling.getOccurredAt(), ruling.getEventId(), eventsStored);
    }

    @GetMapping("/{shipmentId}/events")
    public List<ShipmentEventView> getHistory(@PathVariable String shipmentId) {
        List<ShipmentEvent> events = queryService.historyAscending(shipmentId);
        if (events.isEmpty()) {
            // Not explicitly specified for this endpoint by the spec; decided to 404 here too,
            // for consistency with GET /shipments/{id} (documented in NOTES.md).
            throw new ShipmentNotFoundException(shipmentId);
        }
        return events.stream().map(ShipmentEventView::from).toList();
    }
}
