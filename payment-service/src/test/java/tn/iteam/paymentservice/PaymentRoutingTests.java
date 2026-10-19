package tn.iteam.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.gateway.PaymentGatewaySelector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The instrument decides the acquirer.
 *
 * <p>Sending a domestically issued instrument to an acquirer that does not operate
 * locally produces a decline for a card that is perfectly valid — indistinguishable
 * from fraud, and it costs the sale. These assertions pin the routing so a new
 * method cannot silently default to the wrong gateway.
 */
@SpringBootTest
class PaymentRoutingTests {

    @Autowired private PaymentGatewaySelector selector;

    @Test
    void cashOnDeliveryUsesNoGatewayAtAll() {
        assertThat(selector.forMethod(PaymentMethod.COD))
                .as("nothing is charged up front, so nothing must be authorised")
                .isNull();
    }

    @Test
    void tunisianInstrumentsRouteAwayFromTheInternationalAcquirer() {
        for (PaymentMethod method : new PaymentMethod[]{
                PaymentMethod.EDINAR, PaymentMethod.D17, PaymentMethod.CLICTOPAY, PaymentMethod.WALLET}) {

            assertThat(method.isDomestic()).as("%s is domestic", method).isTrue();

            var gateway = selector.forMethod(method);
            assertThat(gateway).as("%s must reach a gateway", method).isNotNull();
            assertThat(gateway.name())
                    .as("%s must not be sent to an international acquirer", method)
                    .isNotEqualTo("stripe");
        }
    }

    @Test
    void internationalCardsRouteToTheInternationalAcquirer() {
        assertThat(selector.forMethod(PaymentMethod.CARD).name()).isEqualTo("fake");
        assertThat(PaymentMethod.CARD.isDomestic()).isFalse();
    }

    @Test
    void unconfiguredLocalGatewayRefusesRatherThanMisrouting() {
        // The default profile has no Tunisian acquirer configured. An e-Dinar payment
        // must fail loudly, not be forwarded to something that will decline it.
        var local = selector.forMethod(PaymentMethod.EDINAR);
        var result = local.authorize(
                new tn.iteam.paymentservice.gateway.PaymentGateway.AuthorizationRequest(
                        1L, 1L, new java.math.BigDecimal("10.00"), "TND", null, "u", "Order 1", null),
                "test-key");

        assertThat(result.status())
                .isEqualTo(tn.iteam.paymentservice.gateway.PaymentGateway.AuthorizationStatus.ERROR);
        assertThat(result.message()).contains("not available yet");
    }
}