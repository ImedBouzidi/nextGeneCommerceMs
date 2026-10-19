package tn.iteam.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Command to charge an order. Written to payment-service's outbox by the REST
 * request and consumed by payment-service's own listener.
 *
 * <p>The amount is deliberately absent: the request thread cannot price the order
 * (that would need the order data it no longer fetches), so the amount is resolved
 * from the order snapshot once the event is processed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRequestedEvent extends DomainEvent {

    public static final String TYPE = "payment.requested";

    private Long orderId;
    private String userId;
    private String paymentMethod;
    private String transactionId;
    private String idempotencyKey;

    /**
     * Outcome the local simulator should produce, e.g. {@code DECLINED}.
     *
     * <p>Only ever set by the browser's test-card form, and honoured exclusively by
     * {@code FakePaymentGateway}. With {@code payment.gateway=stripe} this is ignored,
     * so a production deployment cannot be steered by a request field.
     */
    private String simulatedOutcome;

    public PaymentRequestedEvent() {
        super(TYPE);
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSimulatedOutcome() {
        return simulatedOutcome;
    }

    public void setSimulatedOutcome(String simulatedOutcome) {
        this.simulatedOutcome = simulatedOutcome;
    }
}