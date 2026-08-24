package com.accso.shipmentservive.repository;

import com.accso.shipmentservive.domain.ShipmentEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the one and only insert path for {@link ShipmentEvent} rows.
 * <p>
 * {@code ShipmentEvent.eventId} is an assigned (not generated) primary key. Spring Data's
 * {@code repository.save()} decides whether to INSERT or UPDATE based on whether the entity
 * "looks new" - for an assigned ID that is already non-null, it assumes the row might already
 * exist and issues a {@code merge()}, which would silently overwrite a colliding row instead of
 * failing. That is the opposite of what duplicate detection needs.
 * <p>
 * Using {@link EntityManager#persist} directly forces an INSERT every time, so a colliding
 * {@code eventId} correctly throws instead of silently overwriting the original event.
 * <p>
 * This class is annotated {@code @Repository} (even though it isn't a Spring Data repository)
 * specifically to opt into Spring's {@code PersistenceExceptionTranslationPostProcessor}, which
 * translates the raw JPA/Hibernate constraint-violation exception into Spring's
 * {@link org.springframework.dao.DataIntegrityViolationException} - a stable, vendor-independent
 * type the caller can catch.
 * <p>
 * The insert runs in its own, independent transaction ({@code REQUIRES_NEW}). Two events racing
 * to store the exact same eventId at the exact same instant will both attempt this method; the
 * loser's flush fails and rolls back only this isolated transaction/persistence context, leaving
 * the caller's own transaction (if any) untouched and able to safely run its own fresh read
 * afterwards (see {@code ShipmentEventIngestService}). The explicit {@code flush()} forces the
 * INSERT - and therefore any constraint violation - to happen synchronously here, rather than
 * being deferred to whenever the transaction eventually commits.
 */
@Repository
public class ShipmentEventWriter {

    private final EntityManager entityManager;

    public ShipmentEventWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(ShipmentEvent event) {
        entityManager.persist(event);
        entityManager.flush();
    }
}
