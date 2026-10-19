package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Fields every event in the platform carries.
 *
 * <p>{@code eventId} is the idempotency key of the messaging side: consumers record
 * it in a {@code processed_events} table so a redelivery (Kafka's default is
 * at-least-once) cannot apply the same business effect twice. Without it, a single
 * duplicated message would decrement stock twice.
 *
 * <p>Shared across services on purpose — producer and consumer must agree on the
 * contract, and duplicating these classes per service is how the two copies drift.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class DomainEvent {

    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private Instant occurredAt = Instant.now();

    protected DomainEvent(String eventType) {
        this.eventType = eventType;
    }

    public DomainEvent() {
    }
}