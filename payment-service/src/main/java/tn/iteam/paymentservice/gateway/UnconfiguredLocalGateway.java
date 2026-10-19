package tn.iteam.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Stand-in for a local Tunisian acquirer that has not been configured.
 *
 * <p>Exists so the application still starts with only international credentials. It
 * refuses every authorisation with a clear message instead of quietly forwarding an
 * e-Dinar or D17 payment to a gateway that cannot process it — which would show the
 * customer a decline for a card that is perfectly valid.
 */
@Slf4j
public class UnconfiguredLocalGateway implements PaymentGateway {

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request, String idempotencyKey) {
        log.error("Refusing a domestic payment for order {}: no local acquirer configured. "
                + "Set payment.gateway.local=konnect with credentials.", request.orderId());
        return AuthorizationResult.error(
                "This payment method is not available yet — no Tunisian payment provider is configured");
    }

    @Override
    public boolean capture(String authorizationId, String idempotencyKey) {
        log.error("capture called on unconfigured local gateway for {}", authorizationId);
        return false;
    }

    @Override
    public void cancel(String authorizationId, String idempotencyKey) {
        // Nothing was ever authorised, so there is nothing to release.
        log.warn("cancel on unconfigured local gateway for {} — nothing was held", authorizationId);
    }

    @Override
    public boolean refund(String chargeId, BigDecimal amount, String idempotencyKey) {
        log.error("refund called on unconfigured local gateway for {}", chargeId);
        return false;
    }

    @Override
    public String name() {
        return "unconfigured";
    }

    /** Configuration for the Konnect acquirer (Central Bank of Tunisia facilitator). */
    @ConfigurationProperties(prefix = "payment.konnect")
    public static class KonnectProperties {
        /** Sandbox base URL: https://sandbox.konnect.network/api/public/payments */
        private String baseUrl = "https://sandbox.konnect.network/api/public/payments";
        /** wallet_id:api_secret from the Konnect dashboard. */
        private String apiKey = "";
        /** Set false to move to production. */
        private boolean sandbox = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }
    }
}