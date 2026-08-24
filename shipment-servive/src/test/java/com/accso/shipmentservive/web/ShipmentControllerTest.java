package com.accso.shipmentservive.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static String newShipmentId() {
        return "ship-" + UUID.randomUUID();
    }

    private String eventJson(String eventId, String shipmentId, String status, String occurredAt, String receivedAt) {
        return """
                {"eventId":"%s","partner":"dhl","shipmentId":"%s","status":"%s","occurredAt":"%s","receivedAt":"%s"}
                """.formatted(eventId, shipmentId, status, occurredAt, receivedAt);
    }

    @Test
    void getState_unknownShipment_returns404() throws Exception {
        mockMvc.perform(get("/shipments/{id}", "ship-does-not-exist-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_unknownShipment_returns404() throws Exception {
        mockMvc.perform(get("/shipments/{id}/events", "ship-does-not-exist-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getState_returnsCurrentStatusRulingEventAndEventsStored() throws Exception {
        String shipmentId = newShipmentId();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-a-" + shipmentId, shipmentId, "IN_TRANSIT",
                        "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")));
        // A stale (older) event: stored, but must not become ruling.
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-stale-" + shipmentId, shipmentId, "LABEL_CREATED",
                        "2026-03-09T00:00:00Z", "2026-03-09T00:00:05Z")));
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-b-" + shipmentId, shipmentId, "OUT_FOR_DELIVERY",
                        "2026-03-10T14:00:00Z", "2026-03-10T14:00:05Z")));

        mockMvc.perform(get("/shipments/{id}", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.currentStatus").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.statusOccurredAt").value("2026-03-10T14:00:00Z"))
                .andExpect(jsonPath("$.rulingEventId").value("evt-b-" + shipmentId))
                .andExpect(jsonPath("$.eventsStored").value(3));
    }

    @Test
    void getHistory_returnsAscendingOrderWithRulingEventLast() throws Exception {
        String shipmentId = newShipmentId();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-b-" + shipmentId, shipmentId, "OUT_FOR_DELIVERY",
                        "2026-03-10T14:00:00Z", "2026-03-10T14:00:05Z")));
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-a-" + shipmentId, shipmentId, "IN_TRANSIT",
                        "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")));

        mockMvc.perform(get("/shipments/{id}/events", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventId").value("evt-a-" + shipmentId))
                .andExpect(jsonPath("$[1].eventId").value("evt-b-" + shipmentId))
                .andExpect(jsonPath("$[1].status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void duplicateAndInvalidAttempts_areNotCountedInEventsStored() throws Exception {
        String shipmentId = newShipmentId();
        String eventId = "evt-" + UUID.randomUUID();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson(eventId, shipmentId, "IN_TRANSIT", "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")));
        // duplicate resubmission
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson(eventId, shipmentId, "IN_TRANSIT", "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")));
        // invalid attempt (bad status) for the same shipment
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-bad-" + shipmentId, shipmentId, "NOT_A_STATUS", "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")));

        mockMvc.perform(get("/shipments/{id}", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsStored").value(1));
    }
}
