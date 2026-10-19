package tn.iteam.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.gateway.UnconfiguredLocalGateway.KonnectProperties;

/**
 * Chooses which acquirer handles a given payment method.
 *
 * <p>Not every gateway can process every instrument. An international acquirer
 * cannot process a domestically issued instrument (e-Dinar, D17, ClicToPay), and a
 * domestic facilitator cannot process a card issued abroad. Routing the wrong way
 * produces a decline for a customer whose payment method is perfectly valid, which
 * is indistinguishable from fraud.
 *
 * <p>So the method selects the gateway:
 * <pre>
 *   COD                          → none, nothing is charged
 *   CARD, PAYPAL, BANK_TRANSFER  → international gateway
 *   EDINAR, D17, CLICTOPAY,
 *   WALLET                       → local gateway
 * </pre>
 *
 * <p>Kept deliberately separate from {@link PaymentGateway}: the selector is not
 * itself a gateway, and implementing the interface here would make it a candidate
 * for its own autowiring — a circular reference that fails at startup.
 */
@Slf4j
@Configuration
public class PaymentGatewaySelector {

    private final PaymentGateway international;
    private final PaymentGateway local;

    public PaymentGatewaySelector(@Qualifier("internationalGateway") PaymentGateway international,
                                  @Qualifier("localGateway") PaymentGateway local) {
        this.international = international;
        this.local = local;
        log.info("Payment routing: international={} local={}", international.name(), local.name());
    }

    /**
     * @return the gateway that must handle this method, or {@code null} when no money
     *         is involved (cash on delivery) and no gateway must be called.
     */
    public PaymentGateway forMethod(PaymentMethod method) {
        if (method == null) return null;
        if (!method.requiresAuthorization()) return null;          // COD
        return method.isDomestic() ? local : international;
    }

    /** The gateway to use for every method; used by admin and refund operations. */
    public PaymentGateway any() {
        return international;
    }

    @Configuration
    static class Beans {

        /**
         * Acquirer for cards issued outside the local market.
         *
         * <p>Not something to be improvised for a Tunisian merchant: an
         * international acquirer does not operate in Tunisia, so this exists for
         * serving customers abroad and is expected to be unused domestically.
         */
        @Bean("internationalGateway")
        @ConditionalOnProperty(
                name = "payment.gateway.international", havingValue = "stripe")
        PaymentGateway stripeGateway(@Value("${payment.stripe.secret-key:}") String secretKey) {
            return new StripePaymentGateway(secretKey);
        }

        @Bean("internationalGateway")
        @ConditionalOnProperty(
                name = "payment.gateway.international", havingValue = "fake", matchIfMissing = true)
        PaymentGateway fakeInternationalGateway() {
            return new FakePaymentGateway();
        }

        /**
         * Acquirer for Tunisian instruments — e-Dinar, D17, ClicToPay, local wallets.
         */
        @Bean("localGateway")
        @ConditionalOnProperty(
                name = "payment.gateway.local", havingValue = "konnect")
        PaymentGateway konnectGateway(KonnectProperties properties) {
            return new KonnectGateway(properties);
        }

        /**
         * No local acquirer configured.
         *
         * <p>Fails loudly instead of quietly routing an e-Dinar payment to the
         * international gateway, which would decline a valid Tunisian card and look
         * like fraud to the customer and to the issuer.
         */
        @Bean("localGateway")
        @ConditionalOnMissingBean(name = "localGateway")
        PaymentGateway unconfiguredLocalGateway() {
            return new UnconfiguredLocalGateway();
        }
    }
}