package tn.iteam.paymentservice.controller;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.repos.PaymentRepository;
import tn.iteam.paymentservice.repos.ProcessedEventRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Stripe webhook receiver.
 *
 * <p>This endpoint — not the browser redirect — is the authoritative signal that a
 * payment happened. A customer's browser can be closed mid-payment, replayed, or
 * tampered with; Stripe's server-side event cannot.
 *
 * <p>Two protections, both mandatory:
 * <ul>
 *   <li><b>Signature verification.</b> Without {@code STRIPE_WEBHOOK_SECRET} the
 *       endpoint refuses everything, because an unverified webhook is just anyone
 *       being able to POST "this payment succeeded" and mark orders paid.</li>
 *   <li><b>Idempotency.</b> Stripe redelivers, so each {@code event.getId()} is
 *       recorded in {@code processed_events}; a repeat is acknowledged and ignored.</li>
 * </ul>
 *
 * <p>Returns 200 quickly after recording the event. Heavy work belongs on the saga,
 * not on Stripe's retry loop.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
@Slf4j
public class StripeWebhookController {

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final String webhookSecret;

    public StripeWebhookController(PaymentRepository paymentRepository,
                                   ProcessedEventRepository processedEventRepository,
                                   @Value("${payment.stripe.webhook-secret:}") String webhookSecret) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> onStripeEvent(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured — refusing. "
                    + "Set it to `stripe listen --print-secret` output for local testing.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "webhook secret not configured"));
        }

        Event event;
        try {
            // Throws if the signature does not match — that is the whole point.
            event = new com.stripe.StripeClient("unused-for-webhooks")
                    .constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            log.warn("Rejected Stripe webhook with bad signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid signature"));
        }

        // Stripe retries anything that is not 2xx, so duplicates are expected traffic.
        if (processedEventRepository.existsById(event.getId())) {
            log.debug("Stripe event {} already processed — acknowledging duplicate", event.getId());
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handleSucceeded(event);
            case "payment_intent.payment_failed" -> handleFailed(event);
            case "charge.refunded" -> handleRefunded(event);
            default -> log.debug("Ignoring Stripe event type {}", event.getType());
        }

        processedEventRepository.save(new tn.iteam.paymentservice.model.ProcessedEvent(
                event.getId(), "stripe-webhook"));
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /** 3-D Secure completed out of band: the held authorisation can now be captured. */
    private void handleSucceeded(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getData().getObject();
        Optional<Payment> maybe = paymentRepository.findByAuthorizationId(intent.getId());
        if (maybe.isEmpty()) {
            log.warn("payment_intent.succeeded for unknown authorisation {}", intent.getId());
            return;
        }
        Payment payment = maybe.get();
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setCapturedAt(LocalDateTime.now());
        payment.setFailureReason(null);
        paymentRepository.save(payment);
        log.info("Payment {} confirmed by Stripe webhook ({})", payment.getId(), intent.getId());
    }

    private void handleFailed(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getData().getObject();
        paymentRepository.findByAuthorizationId(intent.getId()).ifPresent(payment -> {
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                return;   // captured before the failure event arrived
            }
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason(intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getMessage() : "Payment failed");
            payment.setFailureCode(intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getCode() : "payment_failed");
            paymentRepository.save(payment);
            log.info("Payment {} failed per Stripe webhook: {}", payment.getId(), payment.getFailureReason());
        });
    }

    private void handleRefunded(Event event) {
        // A refund happens outside the saga (support action, dispute). Reconciliation
        // would also pick this up; the webhook just makes it immediate.
        log.info("charge.refunded received for {}", event.getId());
    }
}