package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Fact: the reservation succeeded. payment-service finalises the payment on this. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReservedEvent extends DomainEvent {

    public static final String TYPE = "stock.reserved";

    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer remainingQuantity;

    public StockReservedEvent() {
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }
}