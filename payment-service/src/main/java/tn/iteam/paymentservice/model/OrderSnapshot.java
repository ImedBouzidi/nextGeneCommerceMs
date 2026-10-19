package tn.iteam.paymentservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Local projection of an order, built from {@code order.created} events.
 *
 * <p>This is what replaced the synchronous {@code GET /api/orders/{id}} call. Paying
 * for an order still requires the order total, and it must not come from the browser,
 * so it comes from here instead — order-service remains the single source of truth
 * for pricing, and this table is just a cache of what it said.
 *
 * <p>Consequence to be aware of: this is eventually consistent. A payment that
 * arrives before its {@code order.created} event has been consumed finds no row, and
 * the consumer retries rather than pricing from client input.
 */
@Entity
@Table(name = "order_snapshots")
@Getter
@Setter
public class OrderSnapshot {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "qty_ordered")
    private Integer qtyOrdered;

    /** Authoritative order total; this is what gets charged. */
    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "source_event_id")
    private String sourceEventId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(orderStatus);
    }
}