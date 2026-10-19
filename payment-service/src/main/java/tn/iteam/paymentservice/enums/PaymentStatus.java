package tn.iteam.paymentservice.enums;

public enum PaymentStatus {
    /** Payment row created, nothing attempted yet. */
    PENDING,
    /** Row claimed, gateway/processor call in flight. */
    PROCESSING,
    /** Terminal: money captured. */
    SUCCESS,
    /** Terminal: money not captured. */
    FAILED,
    /** Terminal: previously SUCCESS, now reversed. */
    REFUNDED
}