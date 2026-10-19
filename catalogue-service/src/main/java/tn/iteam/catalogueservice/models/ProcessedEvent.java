package tn.iteam.catalogueservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    /**
     * Id of a consumed Kafka event. Kafka delivers at-least-once, so without this
     * ledger a redelivered {@code stock.reserve} would decrement the same units twice.
     */
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