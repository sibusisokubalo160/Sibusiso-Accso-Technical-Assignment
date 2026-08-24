package com.accso.shipmentservive.service;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.domain.ShipmentEventOrder;
import com.accso.shipmentservive.repository.ShipmentEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Everything about a shipment's "current view" is derived fresh from the stored events on every
 * call - there is no separate, cached summary row that could drift out of sync with the events
 * table. This is what both {@code GET} endpoints and the ingest outcome decision (APPLIED vs
 * STALE) rely on, so they can never disagree with each other or with the event history.
 */
@Service
public class ShipmentQueryService {

    private final ShipmentEventRepository repository;

    public ShipmentQueryService(ShipmentEventRepository repository) {
        this.repository = repository;
    }

    /**
     * The ruling event for a shipment: the maximum element under {@link ShipmentEventOrder#ASCENDING}.
     * Empty only if the shipment has no stored events at all.
     */
    public Optional<ShipmentEvent> rulingEvent(String shipmentId) {
        return repository.findByShipmentId(shipmentId).stream().max(ShipmentEventOrder.ASCENDING);
    }

    /**
     * All stored events for a shipment, ascending by occurredAt/receivedAt/eventId.
     * The last element is always the ruling event.
     */
    public List<ShipmentEvent> historyAscending(String shipmentId) {
        List<ShipmentEvent> events = repository.findByShipmentId(shipmentId);
        events.sort(ShipmentEventOrder.ASCENDING);
        return events;
    }

    /** Count of stored events for a shipment; 0 means the shipment is unknown. */
    public long countStored(String shipmentId) {
        return repository.countByShipmentId(shipmentId);
    }
}
