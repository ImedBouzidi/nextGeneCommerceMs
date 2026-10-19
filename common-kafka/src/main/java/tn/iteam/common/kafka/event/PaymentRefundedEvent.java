package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Fact: captured money has been returned to the customer.
 *
 * <p>Published by an explicit refund (support action, cancellation after capture), as
 * opposed to {@code payment.failed}, which means the payment never succeeded.
 * order-service marks the order REFUNDED either way.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRefundedEvent extends DomainEvent {

    public static final String TYPE = "payment.refunded";

    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private String currency;

    public PaymentRefundedEvent() {
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
}