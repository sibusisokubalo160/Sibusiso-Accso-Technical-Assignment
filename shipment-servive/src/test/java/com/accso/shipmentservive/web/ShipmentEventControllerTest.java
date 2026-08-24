package com.accso.shipmentservive.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for POST /shipment-events, driving the real HTTP layer with MockMvc.
 * Each test uses its own randomly generated shipmentId/eventId prefixes so tests never
 * collide with each other regardless of Spring test-context caching/reuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShipmentEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String newShipmentId() {
        return "ship-" + UUID.randomUUID();
    }

    private String eventJson(String eventId, String partner, String shipmentId, String status,
                              String occurredAt, String receivedAt, String location) throws Exception {
        var node = objectMapper.createObjectNode();
        putIfNotNull(node, "eventId", eventId);
        putIfNotNull(node, "partner", partner);
        putIfNotNull(node, "shipmentId", shipmentId);
        putIfNotNull(node, "status", status);
        putIfNotNull(node, "occurredAt", occurredAt);
        putIfNotNull(node, "receivedAt", receivedAt);
        putIfNotNull(node, "location", location);
        return objectMapper.writeValueAsString(node);
    }

    private void putIfNotNull(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    @Test
    void firstEventForNewShipment_isApplied() throws Exception {
        String shipmentId = newShipmentId();
        String eventId = "evt-" + UUID.randomUUID();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(eventId, "dhl", shipmentId, "IN_TRANSIT",
                                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", "Amsterdam")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    void laterOccurredAtEvent_becomesApplied_andUpdatesCurrentStatus() throws Exception {
        String shipmentId = newShipmentId();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-a-" + shipmentId, "dhl", shipmentId, "IN_TRANSIT",
                        "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", null)));

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-b-" + shipmentId, "dhl", shipmentId, "OUT_FOR_DELIVERY",
                                "2026-03-10T14:00:00Z", "2026-03-10T14:00:05Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.currentStatus").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void earlierOccurredAtEvent_isStale_currentStatusUnchanged() throws Exception {
        String shipmentId = newShipmentId();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-a-" + shipmentId, "dhl", shipmentId, "OUT_FOR_DELIVERY",
                        "2026-03-10T14:00:00Z", "2026-03-10T14:00:05Z", null)));

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-b-" + shipmentId, "dhl", shipmentId, "IN_TRANSIT",
                                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("STALE"))
                .andExpect(jsonPath("$.currentStatus").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void sameOccurredAt_laterReceivedAt_becomesRuling() throws Exception {
        String shipmentId = newShipmentId();
        String sameOccurredAt = "2026-03-10T12:00:00Z";

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-a-" + shipmentId, "dhl", shipmentId, "IN_TRANSIT",
                        sameOccurredAt, "2026-03-10T12:00:05Z", null)));

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-b-" + shipmentId, "dhl", shipmentId, "OUT_FOR_DELIVERY",
                                sameOccurredAt, "2026-03-10T12:00:10Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.currentStatus").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void sameOccurredAtAndReceivedAt_greaterEventIdBecomesRuling() throws Exception {
        String shipmentId = newShipmentId();
        String sameOccurredAt = "2026-03-10T12:00:00Z";
        String sameReceivedAt = "2026-03-10T12:00:05Z";

        // "evt-b" > "evt-a" under plain string comparison, submitted first so we can prove the
        // *later-submitted-but-lexicographically-smaller* id correctly stays STALE.
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-b-" + shipmentId, "dhl", shipmentId, "OUT_FOR_DELIVERY",
                        sameOccurredAt, sameReceivedAt, null)));

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-a-" + shipmentId, "dhl", shipmentId, "IN_TRANSIT",
                                sameOccurredAt, sameReceivedAt, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("STALE"))
                .andExpect(jsonPath("$.currentStatus").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void statusMeaningNeverOverridesTimestampOrder_laterReturnedAfterEarlierDelivered() throws Exception {
        String shipmentId = newShipmentId();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-delivered-" + shipmentId, "dhl", shipmentId, "DELIVERED",
                        "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", null)));

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-returned-" + shipmentId, "dhl", shipmentId, "RETURNED",
                                "2026-03-11T09:00:00Z", "2026-03-11T09:00:05Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.currentStatus").value("RETURNED"));
    }

    @Test
    void resubmitSameEventIdSamePayload_sequential_returnsDuplicateNoMismatch() throws Exception {
        String shipmentId = newShipmentId();
        String eventId = "evt-" + UUID.randomUUID();
        String body = eventJson(eventId, "dhl", shipmentId, "IN_TRANSIT",
                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", "Amsterdam");

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"))
                .andExpect(jsonPath("$.payloadMismatch").value(false))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    void resubmitSameEventIdDifferentPayload_sequential_returnsDuplicateWithMismatch() throws Exception {
        String shipmentId = newShipmentId();
        String eventId = "evt-" + UUID.randomUUID();

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson(eventId, "dhl", shipmentId, "IN_TRANSIT",
                        "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", "Amsterdam")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(eventId, "dhl", shipmentId, "OUT_FOR_DELIVERY",
                                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", "Amsterdam")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"))
                .andExpect(jsonPath("$.payloadMismatch").value(true))
                // currentStatus reflects the originally stored event, unaffected by the duplicate's payload
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    void missingRequiredField_returnsInvalidWithReasonAndEchoedFields() throws Exception {
        String shipmentId = newShipmentId();
        String eventId = "evt-" + UUID.randomUUID();

        // partner omitted entirely
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(eventId, null, shipmentId, "IN_TRANSIT",
                                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID"))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.currentStatus").doesNotExist());
    }

    @Test
    void unparseableOccurredAt_returnsInvalid() throws Exception {
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-" + UUID.randomUUID(), "dhl", newShipmentId(), "IN_TRANSIT",
                                "not-a-timestamp", "2026-03-10T12:00:05Z", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID"));
    }

    @Test
    void unknownStatusValue_returnsInvalid() throws Exception {
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-" + UUID.randomUUID(), "dhl", newShipmentId(), "LOST_IN_SPACE",
                                "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID"));
    }

    @Test
    void malformedJsonBody_returnsBadRequestInvalidShape() throws Exception {
        mockMvc.perform(post("/shipment-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID"));
    }
}
