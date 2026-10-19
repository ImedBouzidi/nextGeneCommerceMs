package tn.iteam.paymentservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.event.PaymentFailedEvent;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.gateway.PaymentGatewaySelector;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.repos.PaymentRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for payments that stalled mid-saga.
 *
 * <p>The saga has failure paths for "we were told" — a decline, a stock rejection. It
 * has no path for "nobody ever told us": the order event never arrived, catalogue
 * went down mid-flow, or a consumer crashed between two steps. Those leave a payment
 * stuck in PROCESSING with real money held by the acquirer, which the customer cannot
 * see and nobody is looking at.
 *
 * <p>This job closes that gap: any payment that has been in flight longer than the
 * configured budget has its hold released and is marked FAILED, which publishes
 * {@code payment.failed} and gets the order cancelled like any other failure.
 *
 * <p>The grace period is deliberately long. A payment that is merely slow must not be
 * failed — cancelling a live authorisation whose capture is seconds away would take a
 * customer's money and give it back.
 */
@Component
@Slf4j
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final PaymentGatewaySelector selector;
    private final Duration stuckAfter;

    public PaymentReconciliationJob(PaymentRepository paymentRepository,
                                    OutboxWriter outboxWriter,
                                    PaymentGatewaySelector selector,
                                    @Value("${payment.reconciliation.stuck-after-minutes:15}")
                                    long stuckAfterMinutes) {
        this.paymentRepository = paymentRepository;
        this.outboxWriter = outboxWriter;
        this.selector = selector;
        this.stuckAfter = Duration.ofMinutes(stuckAfterMinutes);
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.delay-ms:60000}")
    public void releaseStuckPayments() {
        try {
            List<Payment> stuck = paymentRepository.findStuckInFlight(
                    List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING),
                    LocalDateTime.now().minus(stuckAfter),
                    Pageable.ofSize(100));
            stuck.forEach(this::releaseAndFail);
        } catch (Exception e) {
            log.warn("Reconciliation sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    protected void releaseAndFail(Payment payment) {
        log.warn("Payment {} for order {} has been {} for over {} — releasing",
                payment.getId(), payment.getOrderId(), payment.getPaymentStatus(), stuckAfter.toMinutes());

        // A stuck payment may hold real money. Releasing it is the safe direction:
        // the customer is not charged for an order that never completed. If the
        // payment had already been captured upstream the webhook says so and this is
        // a no-op against the provider's idempotency.
        var acquirer = selector.forMethod(payment.getPaymentMethod());
        if (acquirer != null && payment.getAuthorizationId() != null) {
            try {
                acquirer.cancel(payment.getAuthorizationId(), "reconcile-" + payment.getId());
            } catch (Exception e) {
                log.error("Could not release authorisation {} — it will expire on its own: {}",
                        payment.getAuthorizationId(), e.getMessage());
            }
        }

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Payment did not complete in time");
        payment.setFailureCode("saga_timeout");
        payment.setClientSecret(null);
        paymentRepository.save(payment);

        PaymentFailedEvent failed = new PaymentFailedEvent();
        failed.setPaymentId(payment.getId());
        failed.setOrderId(payment.getOrderId());
        failed.setReason("Payment did not complete in time");
        outboxWriter.write(PaymentFailedEvent.TYPE, "payment", payment.getId().toString(), failed);
    }

    /** Read-only check used by tests and an ops endpoint to see what is currently stuck. */
    @Transactional(readOnly = true)
    public long stuckCount() {
        return paymentRepository.findStuckInFlight(
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING),
                LocalDateTime.now().minus(stuckAfter),
                Pageable.ofSize(500)).size();
    }
}