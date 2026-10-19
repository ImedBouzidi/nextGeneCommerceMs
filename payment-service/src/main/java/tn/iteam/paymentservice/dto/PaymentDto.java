package tn.iteam.paymentservice.dto;

import lombok.*;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.enums.PaymentStatus;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDto {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private String transactionId;
    /** Echoed back so the client can correlate a replayed request with its result. */
    private String idempotencyKey;
    private String failureReason;

    // ------------------------------------------------------- acquirer integration
    private String currency;
    /**
     * Acquirer reference. Exposed so support can look a charge up in the provider
     * dashboard; it identifies the transaction, it does not authorise anything.
     */
    private String authorizationId;
    /**
     * Returned to the owning client only, and only while a 3-D Secure challenge is
     * outstanding. The frontend passes it to the provider SDK to finish the payment.
     */
    private String clientSecret;
    private String gateway;
    private String failureCode;

    /**
     * Outcome the browser's test-card form wants the fake acquirer to produce.
     *
     * <p>Simulator-only. Never persisted, and ignored outright when
     * {@code payment.gateway=stripe} — the real acquirer decides whether a card is
     * approved, not a request field.
     */
    private String simulatedOutcome;
}