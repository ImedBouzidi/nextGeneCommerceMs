package tn.iteam.paymentservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.PaymentFailedEvent;
import tn.iteam.common.kafka.event.PaymentPaidEvent;
import tn.iteam.common.kafka.event.StockReservationFailedEvent;
import tn.iteam.common.kafka.event.StockReservedEvent;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.gateway.PaymentGateway;
import tn.iteam.paymentservice.gateway.PaymentGatewaySelector;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.model.ProcessedEvent;
import tn.iteam.paymentservice.outbox.OutboxWriter;
import tn.iteam.paymentservice.repos.PaymentRepository;
import tn.iteam.paymentservice.repos.ProcessedEventRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Completes the saga once catalogue-service has answered — and takes the money here.
 *
 * <p>Capture happens at this point, not earlier: the funds were only ever <em>held</em>
 * by the authorisation. That is what makes the failure paths honest:
 * <ul>
 *   <li>stock reserved  → capture → {@code SUCCESS}</li>
 *   <li>capture refused → the hold is released and the payment fails. Stock is
 *       released by order-service reacting to {@code payment.failed}.</li>
 *   <li>stock refused   → cancel the hold → {@code FAILED}. The customer is never
 *       charged for an order that cannot be fulfilled.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmationConsumer {

    private static final String CONSUMER = "payment-service-confirmation";

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outboxWriter;
    private final PaymentGatewaySelector selector;

    @KafkaListener(topics = Topics.STOCK_RESERVED, groupId = "payment-service-confirmation")
    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        if (processedEventRepository.existsById(event.getEventId())) {
            log.debug("stock.reserved {} already handled — skipping duplicate", event.getEventId());
            return;
        }

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (payment == null) {
            log.error("stock.reserved for order {} with no payment row", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.debug("Payment {} already SUCCESS — duplicate confirmation ignored", payment.getId());
            markProcessed(event.getEventId());
            return;
        }

        // Last gate before the money moves. Resolved from the payment's own gateway
        // so a Tunisian instrument is never captured through the wrong acquirer.
        PaymentGateway acquirer = selector.forMethod(payment.getPaymentMethod());
        if (acquirer != null && payment.getAuthorizationId() != null) {
            boolean captured = acquirer.capture(payment.getAuthorizationId(), payment.getIdempotencyKey());
            if (!captured) {
                // Stock is reserved but we could not take the money. Do not publish
                // payment.paid: the order must be unwound instead.
                releaseAndFail(payment, "The bank did not authorise the capture");
                markProcessed(event.getEventId());
                return;
            }
            payment.setCapturedAt(LocalDateTime.now());
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        payment.setClientSecret(null);   // no longer needed; do not hand it out again
        paymentRepository.save(payment);

        PaymentPaidEvent paid = new PaymentPaidEvent();
        paid.setPaymentId(payment.getId());
        paid.setOrderId(payment.getOrderId());
        paid.setUserId(payment.getUserId());
        paid.setAmount(payment.getAmount());
        paid.setCurrency(payment.getCurrency());
        paid.setPaymentMethod(payment.getPaymentMethod() == null ? null : payment.getPaymentMethod().name());
        paid.setItems(List.of(new PaymentPaidEvent.Item(event.getProductId(), event.getQuantity())));
        outboxWriter.write(PaymentPaidEvent.TYPE, "payment", payment.getId().toString(), paid);

        log.info("Payment {} for order {} captured and confirmed ({} {})",
                payment.getId(), payment.getOrderId(), payment.getCurrency(), payment.getAmount());

        markProcessed(event.getEventId());
    }

    @KafkaListener(topics = Topics.STOCK_RESERVATION_FAILED, groupId = "payment-service-confirmation")
    @Transactional
    public void onStockReservationFailed(StockReservationFailedEvent event) {
        if (processedEventRepository.existsById(event.getEventId())) {
            log.debug("stock.reservation-failed {} already handled — skipping duplicate", event.getEventId());
            return;
        }

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (payment == null) {
            log.error("stock.reservation-failed for order {} with no payment row", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }

        releaseAndFail(payment, event.getReason());
        markProcessed(event.getEventId());
    }

    /**
     * Releases any held funds and fails the payment.
     *
     * <p>Cancel rather than refund: at this point the money was authorised but never
     * captured, so there is nothing to give back — the hold simply expires. Refunding
     * here would be wrong twice over, creating a charge to immediately reverse.
     */
    private void releaseAndFail(Payment payment, String reason) {
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.warn("Payment {} already captured — releasing {} instead of cancelling",
                    payment.getId(), payment.getAmount());
            selector.any().refund(payment.getAuthorizationId(), payment.getAmount(), payment.getIdempotencyKey());
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(LocalDateTime.now());
            payment.setFailureReason(reason);
            paymentRepository.save(payment);
            return;
        }

        PaymentGateway acquirer = selector.forMethod(payment.getPaymentMethod());
        if (acquirer != null && payment.getAuthorizationId() != null) {
            try {
                acquirer.cancel(payment.getAuthorizationId(), payment.getIdempotencyKey());
            } catch (Exception e) {
                // The hold expires on its own; reconciliation picks up the rest.
                log.error("Could not cancel authorisation {} — it will expire: {}",
                        payment.getAuthorizationId(), e.getMessage());
            }
        }

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setFailureCode("stock_unavailable");
        payment.setClientSecret(null);
        paymentRepository.save(payment);

        PaymentFailedEvent failed = new PaymentFailedEvent();
        failed.setPaymentId(payment.getId());
        failed.setOrderId(payment.getOrderId());
        failed.setReason(reason);
        outboxWriter.write(PaymentFailedEvent.TYPE, "payment", payment.getId().toString(), failed);

        log.warn("Payment {} for order {} failed: {}", payment.getId(), payment.getOrderId(), reason);
    }

    private void markProcessed(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER));
        } catch (DataIntegrityViolationException e) {
            log.debug("Event {} was processed concurrently", eventId);
        }
    }
}