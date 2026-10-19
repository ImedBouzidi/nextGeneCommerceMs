package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fact: stock has been reserved and the payment is captured.
 *
 * <p>Published only after catalogue-service confirms the reservation, so a consumer
 * that reacts to this event can treat the order as genuinely paid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentPaidEvent extends DomainEvent {

    public static final String TYPE = "payment.paid";

    private Long paymentId;
    private Long orderId;
    private String userId;
    private BigDecimal amount;
    /** ISO-4217. Consumers must never assume the platform's default currency. */
    private String currency;
    private String paymentMethod;
    private List<Item> items;

    public PaymentPaidEvent() {
        super(TYPE);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private Long productId;
        private Integer quantity;

        public Item() {
        }

        public Item(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
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
}