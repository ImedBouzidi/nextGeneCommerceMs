package tn.iteam.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        // The guarantee behind "only the first click can do the job". Application
        // level checks cannot close the race between two concurrent requests, so
        // the constraint has to live in the database.
        @UniqueConstraint(name = "uq_payments_order_id", columnNames = "order_id"),
        @UniqueConstraint(name = "uq_payments_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uq_payments_transaction_id", columnNames = "transaction_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fetched from order-service
    private Long orderId;

    /** Money. Was Double; binary floating point cannot represent cents. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String transactionId;

    /** Client-supplied key (Idempotency-Key header) that identifies this attempt. */
    private String idempotencyKey;

    /** JWT subject of the payer, used for object-level authorisation on reads. */
    private String userId;

    // ------------------------------------------------------- acquirer integration

    /**
     * ISO-4217 code. Required as soon as real money moves: an amount with no currency
     * is ambiguous, and guessing it from the country is how an order priced in TND
     * gets charged in EUR.
     */
    private String currency;

    /** Acquirer's authorisation/charge id (e.g. {@code pi_3abc…}). */
    private String authorizationId;

    /**
     * Secret the frontend hands to the provider SDK to finish a 3-D Secure challenge.
     * Not a card number — safe to return to the owning client, never to anyone else.
     */
    private String clientSecret;

    /** Which gateway handled it: {@code fake} or {@code stripe}. */
    private String gateway;

    /** Machine-readable issuer reason ({@code insufficient_funds}, …) for support and metrics. */
    private String failureCode;

    private LocalDateTime authorizedAt;
    private LocalDateTime capturedAt;
    private LocalDateTime refundedAt;

    /** Populated when the status is FAILED; safe to surface to the client. */
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}