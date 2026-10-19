package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Fact: a payment could not be completed. Consumed by order-service so the order
 * does not sit in PENDING forever with no explanation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent extends DomainEvent {

    public static final String TYPE = "payment.failed";

    private Long paymentId;
    private Long orderId;
    private String reason;

    public PaymentFailedEvent() {
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}