package tn.iteam.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId = UUID.randomUUID().toString(); // use UUID for idempotency

    @Column(name = "aggregate", nullable = false)
    private String aggregate;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Plain TEXT — deliberately NOT {@code @Lob}.
     *
     * <p>{@code @Lob} maps a String to {@code CLOB}, and the Postgres driver then
     * stores the value as a large object, leaving only its OID in the column. Every
     * row looked fine through JPA (the entity cache returned the in-memory string)
     * while the database actually held "43941" — so every published event would have
     * been unparseable for the consumer. It stayed invisible because nothing read the
     * payload back out of the database. A JSON event string in a TEXT column needs no
     * LOB handling.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false)
    private String status = "NEW"; // NEW, SENT, FAILED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts")
    private int attempts = 0;

    public OutboxEvent(String s, String payment, String string, String payload) {
        this.eventType = s;
        this.aggregate = payment;
        this.aggregateId = string;
        this.payload = payload;
        this.status = "NEW";
        this.createdAt = Instant.now();
    }
}
