package tn.iteam.paymentservice.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe adapter.
 *
 * <p>Enabled with {@code payment.gateway.international=stripe}. Use <b>test keys</b> while
 * developing: {@code sk_test_…} only ever moves test money, and {@code 4242 4242 4242
 * 4242} succeeds while {@code 4000 0000 0000 0002} declines.
 *
 * <p>Authorisation is created with {@code capture_method=manual}: Stripe holds the
 * funds and the saga only captures once stock is reserved, so a stock failure can
 * release the hold instead of stranding the customer's money.
 *
 * <p>The card itself is never handled here. The frontend tokenises it with Stripe
 * Elements and sends a payment-method id (or confirms a client secret), so no PAN,
 * CVV or expiry reaches this service — that is what keeps the deployment in PCI
 * SAQ-A scope.
 */
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final StripeClient stripe;

    public StripePaymentGateway(@Value("${payment.stripe.secret-key:}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "payment.gateway.international=stripe requires payment.stripe.secret-key (use a sk_test_ key)");
        }
        if (!secretKey.startsWith("sk_test_") && log.isWarnEnabled()) {
            log.warn("Stripe key does not look like a TEST key. Charges here are real.");
        }
        this.stripe = new StripeClient(secretKey);
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request, String idempotencyKey) {
        if (request.simulatedOutcome() != null && !request.simulatedOutcome().isBlank()) {
            // Defence in depth: the outcome hint exists so the local simulator can
            // pick an issuer decision. Against a real acquirer it must have no effect,
            // or anyone could POST one and choose their own decline reason.
            log.warn("Ignoring simulatedOutcome '{}' from request — the real acquirer decides",
                    request.simulatedOutcome());
        }

        // Stripe amounts are in the currency's smallest unit (e.g. cents).
        long amountInSmallestUnit = request.amount()
                .movePointRight(currencyDigits(request.currency()))
                .longValueExact();

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(request.currency().toLowerCase())
                // Hold the funds; the saga decides when to actually take them.
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setConfirm(true)
                .setDescription(request.description());

        // Stamps our identifiers onto the charge so a Stripe payment, a dashboard
        // entry and a webhook can all be traced back to one order.
        for (var entry : metadata(request).entrySet()) {
            params.putMetadata(entry.getKey(), entry.getValue());
        }

        if (request.providerPaymentMethodId() != null && !request.providerPaymentMethodId().isBlank()) {
            params.setPaymentMethod(request.providerPaymentMethodId());
        }

        try {
            PaymentIntent intent = stripe.paymentIntents().create(params.build(),
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());

            return switch (intent.getStatus()) {
                case "requires_capture" -> AuthorizationResult.authorized(
                        intent.getId(), intent.getClientSecret());
                case "requires_action" -> AuthorizationResult.requiresAction(
                        intent.getId(), intent.getClientSecret());
                case "succeeded", "processing" -> AuthorizationResult.authorized(
                        intent.getId(), intent.getClientSecret());
                default -> AuthorizationResult.declined(
                        intent.getLastPaymentError() != null
                                ? intent.getLastPaymentError().getDeclineCode()
                                : "unexpected_status",
                        intent.getLastPaymentError() != null
                                ? intent.getLastPaymentError().getMessage()
                                : "Unexpected payment status: " + intent.getStatus());
            };

        } catch (com.stripe.exception.CardException e) {
            // The issuer said no. Not retryable, and safe to show to the customer.
            String code = e.getStripeError() != null ? e.getStripeError().getDeclineCode() : "card_declined";
            log.warn("Card declined for order {}: {}", request.orderId(), code);
            return AuthorizationResult.declined(code, e.getMessage());

        } catch (StripeException e) {
            // Network trouble, rate limiting, Stripe outage. Retrying is correct —
            // and the idempotency key guarantees the retry cannot double-charge.
            log.error("Stripe authorisation failed for order {}", request.orderId(), e);
            return AuthorizationResult.error(e.getMessage());
        }
    }

    @Override
    public boolean capture(String authorizationId, String idempotencyKey) {
        try {
            // No params: Stripe captures the full authorised amount. Partial captures
            // would need an explicit amount and are not something this saga supports —
            // orders are captured whole or not at all.
            stripe.paymentIntents().capture(
                    authorizationId,
                    PaymentIntentCaptureParams.builder().build(),
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());
            return true;
        } catch (StripeException e) {
            log.error("Stripe capture failed for {}", authorizationId, e);
            return false;
        }
    }

    @Override
    public void cancel(String authorizationId, String idempotencyKey) {
        try {
            stripe.paymentIntents().cancel(
                    authorizationId,
                    PaymentIntentCancelParams.builder().build(),
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());
        } catch (StripeException e) {
            // The hold expires on its own, so this is recoverable; the reconciliation
            // job picks up anything still pending later.
            log.error("Stripe cancel failed for {} (hold will expire): {}", authorizationId, e);
        }
    }

    @Override
    public boolean refund(String chargeId, BigDecimal amount, String idempotencyKey) {
        try {
            // Full refund. Partial refunds would need an explicit amount in the
            // smallest currency unit and are not modelled by this saga.
            stripe.refunds().create(
                    RefundCreateParams.builder().setPaymentIntent(chargeId).build(),
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey(idempotencyKey)
                            .build());
            return true;
        } catch (StripeException e) {
            log.error("Stripe refund failed for {}", chargeId, e);
            return false;
        }
    }

    @Override
    public String name() {
        return "stripe";
    }

    private Map<String, String> metadata(AuthorizationRequest request) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", String.valueOf(request.orderId()));
        metadata.put("paymentId", String.valueOf(request.paymentId()));
        if (request.customerReference() != null) {
            metadata.put("customerRef", request.customerReference());
        }
        return metadata;
    }

    /** Stripe expects the smallest currency unit; 3-decimal currencies are rare but real (TND, KWD). */
    private int currencyDigits(String currency) {
        return switch (currency == null ? "" : currency.toUpperCase()) {
            case "TND", "KWD", "BHD", "JOD", "OMR" -> 3;
            case "JPY", "KRW", "VND", "CLP", "ISK" -> 0;
            default -> 2;
        };
    }
}