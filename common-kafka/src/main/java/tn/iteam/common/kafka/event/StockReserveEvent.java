package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Command: hold N units of a product for an order.
 *
 * <p>catalogue-service is the only service that may change stock. It used to be
 * called twice on the happy path (once by payment-service, once by order-service),
 * which is why quantities drifted; there is now a single consumer of this command.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReserveEvent extends DomainEvent {

    public static final String TYPE = "stock.reserve";

    private Long orderId;
    private Long productId;
    private Integer quantity;

    public StockReserveEvent() {
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
}