package com.accso.shipmentservive;

import com.accso.shipmentservive.domain.ShipmentEvent;
import com.accso.shipmentservive.domain.ShipmentStatus;
import com.accso.shipmentservive.repository.ShipmentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the "stored events must survive a service restart" requirement literally: writes an
 * event in one Spring context backed by a file-based H2 database, fully closes that context
 * (simulating a process stop), then opens a brand-new context against the same database file
 * and confirms the event is still there. A purely in-memory datasource would fail this test.
 */
class RestartSurvivalTest {

    @TempDir
    Path tempDir;

    @Test
    void eventPersistedInOneContext_isReadableAfterContextRestart() {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("restart-test").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        String eventId = "evt-restart-" + UUID.randomUUID();

        try (ConfigurableApplicationContext firstRun = startApp(dbUrl)) {
            ShipmentEventRepository repository = firstRun.getBean(ShipmentEventRepository.class);
            repository.save(new ShipmentEvent(eventId, "dhl", "ship-restart", ShipmentStatus.IN_TRANSIT,
                    Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:05Z"), null));
        }

        try (ConfigurableApplicationContext secondRun = startApp(dbUrl)) {
            ShipmentEventRepository repository = secondRun.getBean(ShipmentEventRepository.class);
            Optional<ShipmentEvent> reloaded = repository.findById(eventId);
            assertTrue(reloaded.isPresent(), "event written before restart must still be readable after restart");
        }
    }

    private ConfigurableApplicationContext startApp(String dbUrl) {
        // Passed as command-line-style arguments rather than SpringApplicationBuilder.properties()
        // (which registers them as the lowest-precedence "defaultProperties" source) - arguments
        // outrank classpath application.properties, so this reliably overrides the datasource URL
        // regardless of whichever application.properties file(s) happen to be on the classpath.
        return new SpringApplicationBuilder(ShipmentServiveApplication.class)
                .run(
                        "--spring.datasource.url=" + dbUrl,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.h2.console.enabled=false",
                        "--server.port=0"
                );
    }
}
