package tn.iteam.paymentservice.model;

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
 * <p>Kafka is at-least-once, so {@code payment.requested} or
 * {@code stock.reserved} can be delivered twice. Without this table a duplicate
 * {@code stock.reserved} would emit a second {@code payment.paid}, which would make
 * order-service and any future consumer see two charges for one order.
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