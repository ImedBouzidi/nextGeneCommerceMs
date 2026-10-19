package tn.iteam.paymentservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.PaymentFailedEvent;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.common.kafka.event.StockReserveEvent;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.gateway.PaymentGateway;
import tn.iteam.paymentservice.gateway.PaymentGatewaySelector;
import tn.iteam.paymentservice.model.OrderSnapshot;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.model.ProcessedEvent;
import tn.iteam.paymentservice.outbox.OutboxWriter;
import tn.iteam.paymentservice.repos.OrderSnapshotRepository;
import tn.iteam.paymentservice.repos.PaymentRepository;
import tn.iteam.paymentservice.repos.ProcessedEventRepository;
import tn.iteam.paymentservice.util.UserIdentity;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Drives the payment saga: authorise, then ask for stock.
 *
 * <p>Nothing here runs on the customer's request thread any more. The REST call has
 * already returned 202 by the time this consumes the event, which is why this method
 * can afford to retry (the order snapshot may not have arrived yet) and why a
 * downstream hiccup no longer surfaces as a failed payment to the user.
 *
 * <p>Flow, with real money:
 * <pre>
 *   POST /api/payments ──▶ payment row (PENDING) + outbox(payment.requested)
 *        └──▶ this consumer ──▶ PSP.authorise(capture=false)   ← funds HELD
 *                             └──▶ outbox(stock.reserve)
 *                                      └──▶ catalogue reserves stock
 *                                               └──▶ stock.reserved ──▶ capture ──▶ payment.paid
 *                                               └──▶ stock.reservation-failed ──▶ cancel hold
 * </pre>
 *
 * <p>Authorisation happens <em>before</em> stock and capture <em>after</em>. That order
 * is the whole point: if stock cannot be reserved the hold is released and the
 * customer is never charged, instead of taking their money for an order that cannot
 * be fulfilled.
 *
 * <p>Cash on delivery skips the provider entirely — there is nothing to authorise.
 */
@Component
@Slf4j
public class PaymentRequestedConsumer {

    private static final String CONSUMER = "payment-service-requested";

    private final PaymentRepository paymentRepository;
    private final OrderSnapshotRepository snapshotRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outboxWriter;
    private final PaymentGatewaySelector selector;
    private final String currency;

    public PaymentRequestedConsumer(PaymentRepository paymentRepository,
                                    OrderSnapshotRepository snapshotRepository,
                                    ProcessedEventRepository processedEventRepository,
                                    OutboxWriter outboxWriter,
                                    PaymentGatewaySelector selector,
                                    @Value("${payment.currency:TND}") String currency) {
        this.paymentRepository = paymentRepository;
        this.snapshotRepository = snapshotRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxWriter = outboxWriter;
        this.selector = selector;
        this.currency = currency;
    }

    @KafkaListener(topics = Topics.PAYMENT_REQUESTED, groupId = "payment-service-requested")
    @Transactional
    public void onPaymentRequested(PaymentRequestedEvent event) {
        if (processedEventRepository.existsById(event.getEventId())) {
            log.debug("payment.requested {} already handled — skipping duplicate", event.getEventId());
            return;
        }

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (payment == null) {
            log.error("payment.requested for order {} but no payment row exists", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.debug("Order {} already paid — ignoring duplicate request", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }

        Optional<OrderSnapshot> maybeOrder = snapshotRepository.findByOrderId(event.getOrderId());
        if (maybeOrder.isEmpty()) {
            // The order.created event has not been consumed yet. Throwing asks Kafka
            // for a redelivery, which closes the race without pricing from client
            // input. After the configured retries the message is dead-lettered and
            // the payment stays PENDING for manual reconciliation.
            throw new OrderSnapshotNotReadyException(
                    "Order " + event.getOrderId() + " is not in the local read model yet");
        }
        OrderSnapshot order = maybeOrder.get();

        // ---- validation: everything that can reject the payment happens before any
        //      money moves and before stock is touched
        if (order.getUserId() != null && !order.getUserId().equals(UserIdentity.toUserId(event.getUserId()))) {
            failPayment(payment, "Order does not belong to the payer", "forbidden", event.getEventId());
            return;
        }
        if (order.isCancelled()) {
            failPayment(payment, "Order was cancelled", "order_cancelled", event.getEventId());
            return;
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            failPayment(payment, "Order has no payable amount", "invalid_amount", event.getEventId());
            return;
        }
        if (order.getQtyOrdered() == null || order.getQtyOrdered() <= 0) {
            failPayment(payment, "Order has no items", "empty_order", event.getEventId());
            return;
        }

        // ---- amount is decided here, from order data, never from the request body
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(currency);
        payment.setGateway(selector.any().name());
        paymentRepository.save(payment);

        // ---- authorise (COD has nothing to hold)
        PaymentGateway acquirer = selector.forMethod(payment.getPaymentMethod());
        if (acquirer != null) {
            if (!authorizeCard(payment, order, event, acquirer)) {
                return;   // outcome already recorded
            }
        }

        payment.setPaymentStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        StockReserveEvent reserve = new StockReserveEvent();
        reserve.setOrderId(order.getOrderId());
        reserve.setProductId(order.getProductId());
        reserve.setQuantity(order.getQtyOrdered());
        outboxWriter.write(StockReserveEvent.TYPE, "order", order.getOrderId().toString(), reserve);

        log.info("Payment {} for order {} validated ({} {}), stock reservation requested",
                payment.getId(), order.getOrderId(), currency, order.getTotalAmount());

        markProcessed(event.getEventId());
    }

    /**
     * Holds the funds. Returns false when the payment has been failed and needs no
     * further processing.
     */
    private boolean authorizeCard(Payment payment, OrderSnapshot order, PaymentRequestedEvent event,
                                  PaymentGateway acquirer) {
        String eventId = event.getEventId();
        PaymentGateway.AuthorizationResult result = acquirer.authorize(
                new PaymentGateway.AuthorizationRequest(
                        order.getOrderId(),
                        payment.getId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        null,                       // payment method comes from the provider's client SDK
                        payment.getUserId(),
                        "Order " + order.getOrderId(),
                        event.getSimulatedOutcome()),   // simulator hint; ignored by real gateways
                payment.getIdempotencyKey());

        return switch (result.status()) {
            case AUTHORIZED -> {
                payment.setAuthorizationId(result.authorizationId());
                payment.setClientSecret(result.clientSecret());
                payment.setAuthorizedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                log.info("Funds held for payment {} (authorisation {})", payment.getId(), result.authorizationId());
                yield true;
            }
            case REQUIRES_ACTION -> {
                // 3-D Secure or an OTP. The frontend completes the challenge with the
                // client secret; the webhook then finishes this payment.
                payment.setAuthorizationId(result.authorizationId());
                payment.setClientSecret(result.clientSecret());
                payment.setPaymentStatus(PaymentStatus.PROCESSING);
                paymentRepository.save(payment);
                log.info("Payment {} needs customer authentication before capture", payment.getId());
                yield true;
            }
            case DECLINED -> {
                failPayment(payment, result.message(), result.declineCode(), eventId);
                yield false;
            }
            case ERROR -> {
                // Provider outage: retryable. Throwing leaves the payment PENDING and
                // asks Kafka for redelivery, with the same idempotency key so the
                // provider cannot be charged twice for the retry.
                throw new PaymentProviderUnavailableException(
                        "Acquirer " + acquirer.name() + " unavailable: " + result.message());
            }
        };
    }

    /** Marks the payment failed and tells order-service so the order is not left hanging. */
    private void failPayment(Payment payment, String reason, String code) {
        failPayment(payment, reason, code, null);
    }

    /**
     * @param eventId id of the event being handled, recorded as processed so a
     *                redelivery does not repeat the failure notification.
     */
    private void failPayment(Payment payment, String reason, String code, String eventId) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setFailureCode(code);
        paymentRepository.save(payment);

        PaymentFailedEvent failed = new PaymentFailedEvent();
        failed.setPaymentId(payment.getId());
        failed.setOrderId(payment.getOrderId());
        failed.setReason(reason);
        outboxWriter.write(PaymentFailedEvent.TYPE, "payment", payment.getId().toString(), failed);

        log.warn("Payment {} for order {} rejected: {}", payment.getId(), payment.getOrderId(), reason);
        if (eventId != null) {
            markProcessed(eventId);
        }
    }

    private void markProcessed(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER));
        } catch (DataIntegrityViolationException e) {
            // Another instance consumed it first — that is the desired outcome.
            log.debug("Event {} was processed concurrently", eventId);
        }
    }

    /** Signals "not ready yet" so the error handler retries instead of dead-lettering. */
    static class OrderSnapshotNotReadyException extends RuntimeException {
        OrderSnapshotNotReadyException(String message) {
            super(message);
        }
    }

    /**
     * Signals a retryable provider outage.
     *
     * <p>Distinct from {@link OrderSnapshotNotReadyException} on purpose: this one is
     * operational (retry with backoff, then alert) rather than a data race.
     */
    static class PaymentProviderUnavailableException extends RuntimeException {
        PaymentProviderUnavailableException(String message) {
            super(message);
        }
    }
}