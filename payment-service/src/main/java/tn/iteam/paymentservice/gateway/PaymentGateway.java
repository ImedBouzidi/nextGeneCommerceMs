package tn.iteam.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * The seam between the payment saga and a card acquirer.
 *
 * <p>Two implementations exist: {@link StripePaymentGateway} for Stripe, and
 * {@link FakePaymentGateway} for tests and offline development. Keeping the
 * provider behind this interface is what lets the decline, timeout and refund paths
 * be exercised deterministically in CI — they are impossible to provoke reliably
 * against a live sandbox, and impossible at all without one.
 *
 * <p>Lifecycle mirrors a real acquirer and matches how money must move:
 * <pre>
 *   authorize(capture=false)  → money held, not taken      (before stock is reserved)
 *   capture()                 → money actually taken       (only after stock is reserved)
 *   cancel()                  → hold released              (stock could not be reserved)
 *   refund()                  → money returned             (charge succeeded, saga failed later)
 * </pre>
 *
 * <p>Every method takes an {@code idempotencyKey}. Passing it through to the provider
 * is what makes a retry safe on <em>their</em> side too: our unique constraint stops a
 * second row locally, but only the provider's own idempotency stops a second charge.
 */
public interface PaymentGateway {

    /** Outcome of an authorisation attempt. */
    enum AuthorizationStatus {
        /** Funds held; safe to reserve stock. */
        AUTHORIZED,
        /** Issuer said no. Terminal. */
        DECLINED,
        /**
         * The customer must complete an extra challenge (3-D Secure, OTP). The
         * frontend has to return {@link AuthorizationResult#clientSecret} to the
         * provider SDK to finish it, so the saga pauses until the webhook arrives.
         */
        REQUIRES_ACTION,
        /** Provider unreachable or erroring. Not the customer's fault — retryable. */
        ERROR
    }

    AuthorizationResult authorize(AuthorizationRequest request, String idempotencyKey);

    /**
     * Takes the held money. Returns false when the provider refuses; the caller must
     * then release stock and refund rather than treat the order as paid.
     */
    boolean capture(String authorizationId, String idempotencyKey);

    /** Releases an authorisation that was never captured. */
    void cancel(String authorizationId, String idempotencyKey);

    /** Returns money already captured. */
    boolean refund(String chargeId, BigDecimal amount, String idempotencyKey);

    /** Name reported in logs and {@code payment.provider}. */
    String name();

    /**
     * Authorisation request.
     *
     * @param amount    gross amount, already validated against the order total.
     * @param currency  ISO-4217 code. Required — inferring it from the country is how
     *                  an order priced in TND gets charged in EUR.
     * @param providerPaymentMethodId token produced by the provider's client SDK, or
     *                  {@code null} for cash on delivery / provider-hosted card entry.
     * @param simulatedOutcome outcome requested by the browser's test-card form
     *                  (see {@code FakePaymentGateway}). Ignored by real gateways;
     *                  {@code null} in any normal flow.
     */
    record AuthorizationRequest(Long orderId,
                                Long paymentId,
                                BigDecimal amount,
                                String currency,
                                String providerPaymentMethodId,
                                String customerReference,
                                String description,
                                String simulatedOutcome) {
    }

    /**
     * @param clientSecret   pass to the provider's client SDK to complete a challenge.
     * @param declineCode    machine-readable issuer reason, stored for support.
     */
    record AuthorizationResult(AuthorizationStatus status,
                               String authorizationId,
                               String clientSecret,
                               String declineCode,
                               String message) {

        public static AuthorizationResult authorized(String authorizationId, String clientSecret) {
            return new AuthorizationResult(AuthorizationStatus.AUTHORIZED,
                    authorizationId, clientSecret, null, null);
        }

        public static AuthorizationResult declined(String code, String message) {
            return new AuthorizationResult(AuthorizationStatus.DECLINED,
                    null, null, code, message);
        }

        public static AuthorizationResult requiresAction(String authorizationId, String clientSecret) {
            return new AuthorizationResult(AuthorizationStatus.REQUIRES_ACTION,
                    authorizationId, clientSecret, null, null);
        }

        public static AuthorizationResult error(String message) {
            return new AuthorizationResult(AuthorizationStatus.ERROR,
                    null, null, "provider_error", message);
        }
    }
}