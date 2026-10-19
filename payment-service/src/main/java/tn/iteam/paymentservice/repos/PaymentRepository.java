package tn.iteam.paymentservice.repos;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The payment already recorded for this order, if any. */
    Optional<Payment> findByOrderId(Long orderId);

    /** Replay detection for a retried request carrying the same Idempotency-Key. */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** Lookup by acquirer authorisation id — how a Stripe webhook finds its payment. */
    Optional<Payment> findByAuthorizationId(String authorizationId);

    /**
     * Payments that have been in flight since before {@code cutoff}.
     *
     * <p>Used by {@code PaymentReconciliationJob} to find sagas that stalled with the
     * customer's money held at the acquirer. Paged so a large backlog cannot be loaded
     * into memory in one sweep.
     */
    List<Payment> findByPaymentStatusInAndUpdatedAtBefore(
            List<PaymentStatus> statuses, LocalDateTime cutoff, Pageable pageable);

    /**
     * Attempts to claim an order for payment by inserting the row in a single
     * statement.
     *
     * <p>This is the whole double-charge fix. A plain "check then insert" has a
     * window between the SELECT and the INSERT in which a second concurrent
     * request sees no row and inserts its own. Making the insert itself the arbiter
     * means the database accepts exactly one of N concurrent attempts, with no lock
     * held and no read-modify-write race.
     *
     * <p>The conflict target is deliberately omitted ({@code ON CONFLICT DO NOTHING}
     * rather than {@code ON CONFLICT (order_id)}): simultaneous clicks on one order
     * carry the same idempotency key, so the collision can be reported against
     * <em>either</em> unique constraint. Naming only {@code order_id} left the
     * statement throwing on {@code uq_payments_idempotency_key} instead of quietly
     * losing the race, which surfaced as a 500 on the second click.
     *
     * @return {@code true} if this caller won the claim and must carry on with the
     *         charge; {@code false} if the order was already claimed, in which case
     *         the caller must replay the existing payment instead of charging again.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payments (order_id, amount, payment_method, payment_status,
                                  transaction_id, idempotency_key, user_id, created_at, updated_at)
            VALUES (:orderId, :amount, :method, 'PROCESSING',
                    :transactionId, :idempotencyKey, :userId, now(), now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int tryClaimOrder(@Param("orderId") Long orderId,
                      @Param("amount") BigDecimal amount,
                      @Param("method") String method,
                      @Param("transactionId") String transactionId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("userId") String userId);

    /** Default method so {@link #tryClaimOrder} participates in the caller's transaction. */
    @Transactional
    default boolean claimOrder(Long orderId, BigDecimal amount, String method,
                               String transactionId, String idempotencyKey, String userId) {
        return tryClaimOrder(orderId, amount, method, transactionId, idempotencyKey, userId) == 1;
    }

    /** Convenience wrapper for the reconciliation sweep. */
    default List<Payment> findStuckInFlight(List<PaymentStatus> statuses,
                                            LocalDateTime cutoff, Pageable pageable) {
        return findByPaymentStatusInAndUpdatedAtBefore(statuses, cutoff, pageable);
    }
}