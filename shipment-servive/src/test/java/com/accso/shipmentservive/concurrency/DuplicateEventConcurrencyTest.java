package com.accso.shipmentservive.concurrency;

import com.accso.shipmentservive.repository.ShipmentEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the spec's core concurrency requirement: "business rules must hold even when the same
 * event arrives twice at the same instant." N threads race to submit the identical event
 * (same eventId, same payload) concurrently against a real running server; exactly one must be
 * accepted and the rest must be reported as DUPLICATE, with only one row ever stored.
 * <p>
 * Deliberately reads the response as raw JSON ({@link JsonNode}) rather than the server's
 * response DTO - that DTO is write-only (built via static factories for serialization) and has
 * no reason to carry deserialization support just for this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class DuplicateEventConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ShipmentEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void concurrentIdenticalEventIdSubmissions_exactlyOneNonDuplicateOutcome() throws Exception {
        String shipmentId = "ship-concurrent-" + UUID.randomUUID();
        String eventId = "evt-concurrent-" + UUID.randomUUID();
        String body = """
                {"eventId":"%s","partner":"dhl","shipmentId":"%s","status":"IN_TRANSIT",
                 "occurredAt":"2026-03-10T12:00:00Z","receivedAt":"2026-03-10T12:00:05Z"}
                """.formatted(eventId, shipmentId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readySignal = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            tasks.add(() -> {
                readySignal.countDown();
                startSignal.await();
                return restTemplate.postForEntity("/shipment-events", request, String.class);
            });
        }

        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (Callable<ResponseEntity<String>> task : tasks) {
                futures.add(pool.submit(task));
            }
            readySignal.await();
            startSignal.countDown(); // release all threads at once to maximize real overlap

            long nonDuplicateCount = 0;
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get();
                JsonNode json = objectMapper.readTree(response.getBody());
                String outcome = json.get("outcome").asText();
                if (!"DUPLICATE".equals(outcome)) {
                    nonDuplicateCount++;
                    assertEquals("APPLIED", outcome);
                }
            }

            assertEquals(1, nonDuplicateCount, "exactly one concurrent submission of the same eventId must be non-duplicate");
            assertEquals(1, repository.countByShipmentId(shipmentId), "exactly one row must ever be stored for this eventId");
        } finally {
            pool.shutdownNow();
        }
    }
}
