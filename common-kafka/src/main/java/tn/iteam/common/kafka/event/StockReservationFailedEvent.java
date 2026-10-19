package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Fact: the reservation was rejected (typically insufficient stock).
 *
 * <p>payment-service fails the payment and order-service cancels the order, so a
 * failed reservation unwinds the saga instead of leaving a paid order with no stock.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReservationFailedEvent extends DomainEvent {

    public static final String TYPE = "stock.reservation-failed";

    private Long orderId;
    private Long productId;
    private Integer requestedQuantity;
    private Integer availableQuantity;
    private String reason;

    public StockReservationFailedEvent() {
        super(TYPE);
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(Integer requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}