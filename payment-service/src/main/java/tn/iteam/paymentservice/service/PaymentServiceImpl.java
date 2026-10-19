package tn.iteam.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.iteam.common.kafka.event.PaymentRefundedEvent;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.paymentservice.dto.PaymentDto;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.exceptions.BusinessException;
import tn.iteam.paymentservice.gateway.PaymentGateway;
import tn.iteam.paymentservice.gateway.PaymentGatewaySelector;
import tn.iteam.paymentservice.interfaces.PaymentService;
import tn.iteam.paymentservice.mapper.PaymentMapper;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.outbox.OutboxWriter;
import tn.iteam.paymentservice.repos.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Records the customer's intent to pay; everything else happens over Kafka.
 *
 * <p>The old implementation validated the order and product over Feign, charged,
 * flipped the order to PAID and decremented stock — all inside the request. That made
 * a single HTTP call span four services and left no way to recover if any of them
 * was briefly unavailable. Now the request does two things: claim the order, and
 * stage {@code payment.requested}.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OutboxWriter outboxWriter;
    private final PaymentGatewaySelector selector;

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Override
    public PaymentDto createPayment(PaymentDto dto, String userId, String idempotencyKey) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException("orderId is required", HttpStatus.BAD_REQUEST);
        }
        String key = normaliseKey(idempotencyKey, dto.getOrderId());

        // 1️⃣  Replay: this exact request was already accepted. Return the stored
        //     payment — PENDING or SUCCESS — instead of charging again.
        Payment replay = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            log.info("Idempotent replay for key {} (order {}): payment {} is {}",
                    key, dto.getOrderId(), replay.getId(), replay.getPaymentStatus());
            return paymentMapper.toDto(replay);
        }

        // 2️⃣  Claim the order. The unique constraint on order_id is what makes a
        //     double click safe: the insert either wins or is rejected, and losers
        //     replay the winner's payment.
        String transactionId = "tx-" + UUID.randomUUID();
        boolean claimed = paymentRepository.claimOrder(
                dto.getOrderId(), null,
                dto.getPaymentMethod() == null ? null : dto.getPaymentMethod().name(),
                transactionId, key, userId);

        Payment payment;
        if (claimed) {
            payment = paymentRepository.findByOrderId(dto.getOrderId())
                    .orElseThrow(() -> new BusinessException("Payment claim lost", HttpStatus.CONFLICT));
        } else {
            log.info("Order {} already claimed by a concurrent request — replaying existing payment",
                    dto.getOrderId());
            return paymentRepository.findByOrderId(dto.getOrderId())
                    .map(paymentMapper::toDto)
                    .orElseThrow(() -> new BusinessException(
                            "A payment for order " + dto.getOrderId() + " is already in progress",
                            HttpStatus.CONFLICT));
        }

        // 3️⃣  Stage the intent. The amount is deliberately not set here: this method
        //     no longer fetches the order, so the total is resolved from the local
        //     read model by PaymentRequestedConsumer and stored before the payment
        //     can become SUCCESS.
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(key);
        payment.setTransactionId(transactionId);
        payment.setUserId(userId);
        payment = paymentRepository.save(payment);

        PaymentRequestedEvent event = new PaymentRequestedEvent();
        event.setOrderId(payment.getOrderId());
        event.setUserId(userId);
        event.setPaymentMethod(dto.getPaymentMethod() == null ? null : dto.getPaymentMethod().name());
        event.setTransactionId(transactionId);
        event.setIdempotencyKey(key);
        event.setSimulatedOutcome(dto.getSimulatedOutcome());
        outboxWriter.write(PaymentRequestedEvent.TYPE, "payment", payment.getId().toString(), event);

        log.info("Payment {} for order {} accepted (PENDING), event {} staged",
                payment.getId(), payment.getOrderId(), event.getEventId());

        return paymentMapper.toDto(payment);
    }

    @Override
    public PaymentDto getPayment(Long id) {
        return paymentMapper.toDto(findOrThrow(id));
    }

    @Override
    public PaymentDto getPaymentForUser(Long id, String userId) {
        Payment payment = findOrThrow(id);
        assertOwnership(payment, userId);
        return paymentMapper.toDto(payment);
    }

    /**
     * Lookup by order, used by the client to poll while the saga runs. Only the owner
     * (or an admin) may poll someone else's order.
     */
    @Override
    public PaymentDto getPaymentByOrderForUser(Long orderId, String userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("No payment for order " + orderId, HttpStatus.NOT_FOUND));
        assertOwnership(payment, userId);
        return paymentMapper.toDto(payment);
    }

    @Override
    public List<PaymentDto> getAllPayments() {
        return paymentRepository.findAll().stream().map(paymentMapper::toDto).toList();
    }

    @Override
    public List<PaymentDto> getPaymentsForUser(String userId) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getUserId() != null && p.getUserId().equals(userId))
                .map(paymentMapper::toDto)
                .toList();
    }

    @Override
    public PaymentDto updatePayment(Long id, PaymentDto dto) {
        Payment payment = findOrThrow(id);
        payment.setPaymentStatus(dto.getPaymentStatus());
        payment.setFailureReason(dto.getFailureReason());
        return paymentMapper.toDto(paymentRepository.save(payment));
    }

    @Override
    public void deletePayment(Long id) {
        paymentRepository.deleteById(id);
    }

    /**
     * Refunds a captured payment.
     *
     * <p>Only a <em>captured</em> payment can be refunded — refunding an authorisation
     * that was never taken would create a charge and immediately reverse it, which
     * shows up in statements and can trip fraud checks. That case is a
     * {@code cancel}, and the saga already does it when stock fails.
     *
     * <p>The refund is issued before the local status changes, and the acquirer's own
     * idempotency key is the payment id, so a retry after a timeout cannot refund
     * twice.
     */
    @Override
    public PaymentDto refundPayment(Long id, BigDecimal amount, String userId) {
        Payment payment = findOrThrow(id);

        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessException("Payment has already been refunded", HttpStatus.CONFLICT);
        }
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(
                    "Only a captured payment can be refunded (current status: "
                            + payment.getPaymentStatus() + ")", HttpStatus.CONFLICT);
        }
        if (payment.getAuthorizationId() == null) {
            // Cash on delivery: nothing was charged through a provider, so there is
            // nothing to refund at the acquirer either.
            throw new BusinessException(
                    "This payment was not taken through a card provider (cash on delivery)",
                    HttpStatus.CONFLICT);
        }

        BigDecimal refundAmount = (amount == null || amount.signum() <= 0)
                ? payment.getAmount()
                : amount.min(payment.getAmount());

        boolean refunded = selector.forMethod(payment.getPaymentMethod())
                .refund(payment.getAuthorizationId(), refundAmount, "refund-" + payment.getId());
        if (!refunded) {
            // Do not mark it refunded locally when the provider refused: that would
            // hide real money from reconciliation.
            throw new BusinessException("The payment provider refused the refund", HttpStatus.BAD_GATEWAY);
        }

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setFailureReason("Refunded by " + userId);
        paymentRepository.save(payment);

        PaymentRefundedEvent event = new PaymentRefundedEvent();
        event.setPaymentId(payment.getId());
        event.setOrderId(payment.getOrderId());
        event.setAmount(refundAmount);
        event.setCurrency(payment.getCurrency());
        outboxWriter.write(PaymentRefundedEvent.TYPE, "payment", payment.getId().toString(), event);

        log.info("Payment {} refunded ({} {}) by {}", payment.getId(), refundAmount,
                payment.getCurrency(), userId);

        return paymentMapper.toDto(payment);
    }

    // ------------------------------------------------------------------ helpers

    private Payment findOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND));
    }

    private String normaliseKey(String idempotencyKey, Long orderId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey.trim();
        }
        // No key supplied: fall back to one-per-order, still enforced by the unique
        // constraint. Weaker than a real key but never weaker than before.
        return "order-" + orderId;
    }

    private void assertOwnership(Payment payment, String userId) {
        if (payment.getUserId() == null) return; // legacy row, no owner recorded
        if (!payment.getUserId().equals(userId)) {
            log.warn("User {} attempted to read payment {} owned by {}",
                    userId, payment.getId(), payment.getUserId());
            throw new BusinessException("You can only access your own payments", HttpStatus.FORBIDDEN);
        }
    }
}