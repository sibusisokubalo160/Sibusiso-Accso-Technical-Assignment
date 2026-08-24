package com.accso.shipmentservive.repository;

import com.accso.shipmentservive.domain.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, String> {

    List<ShipmentEvent> findByShipmentId(String shipmentId);

    long countByShipmentId(String shipmentId);
}
