package tn.iteam.paymentservice.interfaces;

import tn.iteam.paymentservice.dto.PaymentDto;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {

    /**
     * Creates a payment for an order, at most once per order.
     *
     * <p>Safe to call repeatedly and concurrently: the first caller to claim the
     * order performs the charge, every other caller gets the already-recorded
     * payment back instead of a second charge.
     *
     * @param userId         JWT subject of the payer, used for ownership checks.
     * @param idempotencyKey client-supplied key; falls back to a per-order key when null.
     */
    PaymentDto createPayment(PaymentDto dto, String userId, String idempotencyKey);

    PaymentDto getPayment(Long id);

    /** Owner-or-admin read. Rejects with 403 when {@code userId} does not own the payment. */
    PaymentDto getPaymentForUser(Long id, String userId);

    /** Owner-or-admin read by order; this is what the client polls while the saga runs. */
    PaymentDto getPaymentByOrderForUser(Long orderId, String userId);

    List<PaymentDto> getAllPayments();

    List<PaymentDto> getPaymentsForUser(String userId);

    PaymentDto updatePayment(Long id, PaymentDto dto);

    void deletePayment(Long id);

    /**
     * Returns money for a payment that was already captured, publishing
     * {@code payment.refunded} so the order can be marked REFUNDED.
     *
     * @throws BusinessException if the payment was never captured, or was already
     *                           refunded.
     */
    PaymentDto refundPayment(Long id, BigDecimal amountOrNull, String userId);
}