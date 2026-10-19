package tn.iteam.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tn.iteam.paymentservice.gateway.UnconfiguredLocalGateway.KonnectProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Konnect adapter — a payment facilitator certified by the Central Bank of Tunisia.
 *
 * <p>This is the acquirer that can actually process the instruments a Tunisian
 * customer holds. Konnect accepts Tunisian-issued bank cards, e-Dinar and its own
 * wallet, with 3-D Secure handled in its PCI-DSS hosted checkout. An international
 * acquirer will decline all of them.
 *
 * <p>Differences from a card acquirer that matter here:
 * <ul>
 *   <li><b>Amounts are in millimes</b> (1 TND = 1000), not cents. Getting this wrong
 *       charges a customer 1000x their order total.</li>
 *   <li><b>There is no separate "authorize".</b> Konnect takes an immediate payment
 *       at the hosted checkout and reports the outcome by webhook. So
 *       {@link #authorize} creates the checkout session and returns REQUIRES_ACTION
 *       with the pay URL, and {@link #capture} is a no-op that verifies the session
 *       rather than a second charge — the sandbox's own lifecycle is used, and the
 *       saga's ordering still holds: stock is reserved before the customer pays, and
 *       the webhook releases it if they abandon.</li>
 * </ul>
 *
 * <p>Configure with {@code payment.gateway.local=konnect} and credentials from the
 * Konnect dashboard. Sandbox test cards: 4000000000000002 (declines),
 * 5100000000000008 (approved).
 */
@Slf4j
public class KonnectGateway implements PaymentGateway {

    /** Konnect's accepted payment method codes. */
    private static final List<String> TUNISIAN_METHODS = List.of("bank_card", "e-DINAR", "wallet");

    private final KonnectProperties properties;
    private final RestClient http;

    public KonnectGateway(KonnectProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "payment.gateway.local=konnect requires payment.konnect.api-key "
                    + "(wallet_id:api_secret from the Konnect dashboard)");
        }
        this.properties = properties;
        this.http = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", properties.getApiKey())
                .build();
        log.info("Konnect gateway ready (sandbox={}, baseUrl={})",
                properties.isSandbox(), properties.getBaseUrl());
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request, String idempotencyKey) {
        long millimes = request.amount()
                .multiply(BigDecimal.valueOf(1000))
                .longValueExact();

        Map<String, Object> body = new HashMap<>();
        body.put("amount", millimes);
        body.put("type", "immediate");
        body.put("lifespan", 30);                       // minutes the checkout stays valid
        body.put("order_id", "order-" + request.orderId());
        body.put("description", request.description());
        // Only the methods a Tunisian customer can actually use. Sending an
        // unsupported one makes the checkout show an option that always fails.
        body.put("accepted_payment_methods", TUNISIAN_METHODS);
        body.put("webhook_url", webhookUrl());
        body.put("success_url", "http://localhost:4200/client/orders");
        body.put("fail_url", "http://localhost:4200/client/checkout");

        try {
            Map<?, ?> response = http.post()
                    .uri("/init")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String paymentRef = String.valueOf(response.get("payment_ref"));
            String payUrl = String.valueOf(response.get("pay_url"));

            log.info("Konnect session {} created for order {} ({} millimes)",
                    paymentRef, request.orderId(), millimes);

            // The customer still has to complete the checkout, so this is a
            // challenge, not an authorisation: no money is held until they do.
            return AuthorizationResult.requiresAction(paymentRef, payUrl);

        } catch (Exception e) {
            log.error("Konnect session creation failed for order {}", request.orderId(), e);
            return AuthorizationResult.error("Payment provider unavailable: " + e.getMessage());
        }
    }

    /**
     * No-op: Konnect already captured at checkout.
     *
     * <p>Confirmed against the payment reference rather than blindly returning true,
     * so an abandoned checkout cannot be reported as paid.
     */
    @Override
    public boolean capture(String authorizationId, String idempotencyKey) {
        try {
            Map<?, ?> status = http.get()
                    .uri("/status/{payment_ref}", authorizationId)
                    .retrieve()
                    .body(Map.class);

            Object completed = status.get("completed");
            Object paid = status.get("paid");
            boolean done = Boolean.TRUE.equals(completed) || Boolean.TRUE.equals(paid);

            if (!done) {
                log.warn("Konnect session {} is not completed yet", authorizationId);
            }
            return done;

        } catch (Exception e) {
            log.error("Konnect status check failed for {}", authorizationId, e);
            return false;
        }
    }

    /** Nothing to release — Konnect holds no funds until the customer pays. */
    @Override
    public void cancel(String authorizationId, String idempotencyKey) {
        log.debug("Konnect session {} abandoned; it expires after its lifespan", authorizationId);
    }

    @Override
    public boolean refund(String chargeId, BigDecimal amount, String idempotencyKey) {
        long millimes = amount.multiply(BigDecimal.valueOf(1000)).longValueExact();
        try {
            http.post()
                    .uri("/refund/{payment_ref}", chargeId)
                    .body(Map.of("amount", millimes))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Konnect refund of {} millimes for session {}", millimes, chargeId);
            return true;
        } catch (Exception e) {
            log.error("Konnect refund failed for {}", chargeId, e);
            return false;
        }
    }

    @Override
    public String name() {
        return "konnect";
    }

    private String webhookUrl() {
        return properties.getBaseUrl()
                .replaceFirst("/api/public/payments$", "/api/public/payments/webhook");
    }
}