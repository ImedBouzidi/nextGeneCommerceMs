package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Emitted by order-service when an order is placed.
 *
 * <p>This is how payment-service learns what it is being asked to pay for without
 * calling order-service over HTTP: it keeps a local {@code order_snapshots} read
 * model from these events, so the amount is still computed on the server from
 * order data rather than trusted from the browser.
 *
 * <p>{@code totalAmount} is the authoritative order total and is what gets charged.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCreatedEvent extends DomainEvent {

    public static final String TYPE = "order.created";

    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer qtyOrdered;
    private BigDecimal totalAmount;
    private String orderStatus;

    public OrderCreatedEvent() {
        super(TYPE);
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQtyOrdered() {
        return qtyOrdered;
    }

    public void setQtyOrdered(Integer qtyOrdered) {
        this.qtyOrdered = qtyOrdered;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }
}