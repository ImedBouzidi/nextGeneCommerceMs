package tn.iteam.paymentservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.iteam.paymentservice.model.OutboxEvent;
import tn.iteam.paymentservice.repos.OutboxRepository;

/**
 * Writes a domain event to the outbox table.
 *
 * <p>Events are staged in the same database transaction as the state change they
 * describe. That is the whole point: publishing straight to Kafka from a
 * {@code @Service} method either loses the event if the broker is down, or announces
 * an event for a transaction that later rolls back. {@link OutboxPublisher} drains
 * the table afterwards.
 *
 * <p>Shared by the REST layer (which records the customer's intent) and the
 * consumers (which record what happened next), so every event takes the same route.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Stages {@code payload} for publication. Runs in the caller's transaction; if it
     * rolls back, so does the event.
     *
     * @param aggregateId business key, e.g. the payment id. Also used as the Kafka
     *                    message key, which keeps per-order events on one partition
     *                    and therefore in order.
     */
    public void write(String eventType, String aggregate, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(new OutboxEvent(eventType, aggregate, aggregateId, json));
            log.debug("Staged {} event for aggregate {} (key {})", eventType, aggregate, aggregateId);
        } catch (Exception e) {
            throw new IllegalStateException("Could not stage " + eventType + " event: " + e.getMessage(), e);
        }
    }
}