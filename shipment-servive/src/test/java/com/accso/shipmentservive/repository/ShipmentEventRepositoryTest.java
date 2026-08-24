package com.accso.shipmentservive.repository;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.domain.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ShipmentEventRepositoryTest {

    @Autowired
    private ShipmentEventRepository repository;

    private static ShipmentEvent event(String eventId, String shipmentId) {
        return new ShipmentEvent(eventId, "dhl", shipmentId, ShipmentStatus.IN_TRANSIT,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:05Z"), null);
    }

    @Test
    void findByShipmentId_onlyReturnsEventsForThatShipment() {
        repository.save(event("evt-1", "ship-a"));
        repository.save(event("evt-2", "ship-a"));
        repository.save(event("evt-3", "ship-b"));

        List<ShipmentEvent> shipAEvents = repository.findByShipmentId("ship-a");

        assertEquals(2, shipAEvents.size());
        assertTrue(shipAEvents.stream().allMatch(e -> e.getShipmentId().equals("ship-a")));
    }

    @Test
    void countByShipmentId_excludesOtherShipments() {
        repository.save(event("evt-1", "ship-a"));
        repository.save(event("evt-2", "ship-a"));
        repository.save(event("evt-3", "ship-b"));

        assertEquals(2, repository.countByShipmentId("ship-a"));
        assertEquals(1, repository.countByShipmentId("ship-b"));
        assertEquals(0, repository.countByShipmentId("ship-unknown"));
    }
}
