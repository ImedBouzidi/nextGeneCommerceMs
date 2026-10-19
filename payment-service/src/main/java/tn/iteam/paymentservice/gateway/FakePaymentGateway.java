package tn.iteam.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Offline stand-in for an acquirer.
 *
 * <p>The default implementation, deliberately: with no provider configured the
 * service must never be able to reach a real card network. Setting
 * {@code payment.gateway.international=stripe} opts into {@link StripePaymentGateway}.
 *
 * <p>Two ways to choose an outcome:
 * <ol>
 *   <li><b>By test card number.</b> The browser's simulator sends
 *       {@code simulatedOutcome} derived from the card the tester typed, using
 *       Stripe's own published test cards so the numbers they are used to memorise
 *       behave identically here. This is what makes a local run feel like the real
 *       thing.</li>
 *   <li><b>By amount.</b> A fallback for tests that do not go through the browser —
 *       {@code 0.01} declines for insufficient funds, {@code 0.03} demands 3-D
 *       Secure, {@code 0.04} simulates an outage, {@code 0.05} authorises but refuses
 *       the capture.</li>
 * </ol>
 * Those magic amounts exist so the saga's compensation logic — release stock,
 * refund, cancel — can be tested for real rather than asserted about in prose.
 */
@Slf4j
public class FakePaymentGateway implements PaymentGateway {

    public static final double DECLINE_INSUFFICIENT_FUNDS = 0.01d;
    public static final double DECLINE_GENERIC = 0.02d;
    public static final double REQUIRES_ACTION = 0.03d;
    public static final double PROVIDER_ERROR = 0.04d;
    public static final double CAPTURE_FAILS = 0.05d;

    /** Outcomes the browser simulator may request. Mirrors Stripe's documented test cards. */
    public static final String OUTCOME_AUTHORIZED = "AUTHORIZED";
    public static final String OUTCOME_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String OUTCOME_DECLINED = "DECLINED";
    public static final String OUTCOME_REQUIRES_ACTION = "REQUIRES_ACTION";
    public static final String OUTCOME_EXPIRED_CARD = "EXPIRED_CARD";
    public static final String OUTCOME_PROCESSING_ERROR = "PROCESSING_ERROR";

    private final Map<String, String> authorizations = new ConcurrentHashMap<>();
    /** Idempotency-key → authorisation id, so a retry returns the same hold. */
    private final Map<String, String> byIdempotencyKey = new ConcurrentHashMap<>();
    private final AtomicInteger captureCalls = new AtomicInteger();

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request, String idempotencyKey) {
        // Mirrors the provider's own idempotency behaviour: the same key must never
        // produce a second hold.
        String existing = byIdempotencyKey.get(idempotencyKey);
        if (existing != null) {
            log.info("Fake gateway: replaying authorisation {} for key {}", existing, idempotencyKey);
            return AuthorizationResult.authorized(existing, "fake_secret_" + existing);
        }

        String requested = request.simulatedOutcome();
        if (requested != null && !requested.isBlank()) {
            return authorizeSimulated(request, idempotencyKey, requested.trim().toUpperCase());
        }
        return authorizeByAmount(request, idempotencyKey);
    }

    /** Path taken when the browser's test-card form supplied an outcome. */
    private AuthorizationResult authorizeSimulated(AuthorizationRequest request,
                                                   String idempotencyKey, String outcome) {
        log.info("Fake gateway: simulator requested {} for order {}", outcome, request.orderId());

        return switch (outcome) {
            case OUTCOME_INSUFFICIENT_FUNDS ->
                    AuthorizationResult.declined("insufficient_funds",
                            "Your card has insufficient funds for this purchase.");
            case OUTCOME_DECLINED ->
                    AuthorizationResult.declined("card_declined", "Your card was declined.");
            case OUTCOME_EXPIRED_CARD ->
                    AuthorizationResult.declined("expired_card", "Your card has expired.");
            case OUTCOME_PROCESSING_ERROR ->
                    AuthorizationResult.error("An error occurred while processing your card.");
            case OUTCOME_REQUIRES_ACTION -> {
                String id = newId();
                authorizations.put(id, "REQUIRES_ACTION");
                byIdempotencyKey.put(idempotencyKey, id);
                yield AuthorizationResult.requiresAction(id, "fake_secret_" + id);
            }
            default -> {
                String id = newId();
                authorizations.put(id, "AUTHORIZED");
                byIdempotencyKey.put(idempotencyKey, id);
                yield AuthorizationResult.authorized(id, "fake_secret_" + id);
            }
        };
    }

    /** Path taken by tests that never involve the browser. */
    private AuthorizationResult authorizeByAmount(AuthorizationRequest request, String idempotencyKey) {
        double amount = request.amount().doubleValue();

        if (amount == DECLINE_INSUFFICIENT_FUNDS) {
            return AuthorizationResult.declined("insufficient_funds", "Your card has insufficient funds.");
        }
        if (amount == DECLINE_GENERIC) {
            return AuthorizationResult.declined("card_declined", "Your card was declined.");
        }
        if (amount == REQUIRES_ACTION) {
            String id = newId();
            authorizations.put(id, "REQUIRES_ACTION");
            byIdempotencyKey.put(idempotencyKey, id);
            return AuthorizationResult.requiresAction(id, "fake_secret_" + id);
        }
        if (amount == PROVIDER_ERROR) {
            return AuthorizationResult.error("Acquirer unavailable");
        }

        String id = newId();
        authorizations.put(id, CAPTURE_FAILS == amount ? "CAPTURE_FAILS" : "AUTHORIZED");
        byIdempotencyKey.put(idempotencyKey, id);
        log.info("Fake gateway: authorised {} {} for order {} ({})",
                request.currency(), request.amount(), request.orderId(), id);
        return AuthorizationResult.authorized(id, "fake_secret_" + id);
    }

    @Override
    public boolean capture(String authorizationId, String idempotencyKey) {
        captureCalls.incrementAndGet();
        String state = authorizations.get(authorizationId);
        if (state == null) return false;
        if ("CAPTURE_FAILS".equals(state)) {
            log.warn("Fake gateway: capture refused for {}", authorizationId);
            return false;
        }
        authorizations.put(authorizationId, "CAPTURED");
        return true;
    }

    @Override
    public void cancel(String authorizationId, String idempotencyKey) {
        authorizations.remove(authorizationId);
        log.info("Fake gateway: cancelled authorisation {}", authorizationId);
    }

    @Override
    public boolean refund(String chargeId, BigDecimal amount, String idempotencyKey) {
        log.info("Fake gateway: refunded {} on charge {}", amount, chargeId);
        return true;
    }

    @Override
    public String name() {
        return "fake";
    }

    private String newId() {
        return "fake_auth_" + UUID.randomUUID().toString().substring(0, 8);
    }
}