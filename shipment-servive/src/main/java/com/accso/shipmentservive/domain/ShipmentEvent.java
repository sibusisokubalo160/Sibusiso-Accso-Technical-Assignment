package com.accso.shipmentservive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single, immutable courier webhook event as stored.
 * <p>
 * {@code eventId} is the primary key: it is the natural, caller-assigned identity of a real-world
 * event and is unique and stable across retries (per the spec). That PK/unique constraint is the
 * mechanism that makes duplicate detection correct even when the exact same event is submitted
 * concurrently by two racing requests - see {@link com.accso.shipmentservive.repository.ShipmentEventWriter}.
 * <p>
 * Events are never updated or deleted once stored, so no setters are exposed.
 */
@Entity
@Table(
        name = "shipment_event",
        indexes = @Index(name = "idx_shipment_event_shipment_id", columnList = "shipment_id")
)
public class ShipmentEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(name = "partner", nullable = false, updatable = false)
    private String partner;

    @Column(name = "shipment_id", nullable = false, updatable = false)
    private String shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private ShipmentStatus status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "location", updatable = false)
    private String location;

    protected ShipmentEvent() {
        // required by JPA
    }

    public ShipmentEvent(String eventId, String partner, String shipmentId, ShipmentStatus status,
                          Instant occurredAt, Instant receivedAt, String location) {
        this.eventId = eventId;
        this.partner = partner;
        this.shipmentId = shipmentId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.location = location;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPartner() {
        return partner;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getLocation() {
        return location;
    }
}
