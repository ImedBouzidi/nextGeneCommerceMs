package tn.iteam.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ledger of consumed event ids.
 *
 * <p>Kafka delivers at-least-once, so a {@code payment.paid} message can arrive
 * twice (consumer restart, rebalance, transient failure after the DB commit).
 * Applying the state change twice would move an order to PAID twice and, historically,
 * decrement stock twice. Recording the event id in the same transaction as the state
 * change makes the consumer idempotent — the second delivery hits the primary key and
 * is skipped.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "consumer")
    private String consumer;

    @Column(name = "processed_at")
    private LocalDateTime processedAt = LocalDateTime.now();

    public ProcessedEvent(String eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = LocalDateTime.now();
    }
}