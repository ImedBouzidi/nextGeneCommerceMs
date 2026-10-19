package tn.iteam.paymentservice.enums;

/**
 * How the customer pays.
 *
 * <p>The Tunisian methods exist because this platform is sold for the Tunisian
 * market, where the instruments people actually hold are domestic: postal e-Dinar,
 * the D17 wallet, ClicToPay bank cards. None of them are reachable through a
 * card acquirer that does not operate locally, so they are modelled as first-class
 * methods routed to a local gateway rather than shoehorned into {@link #CARD}.
 */
public enum PaymentMethod {

    /**
     * International card through a hosted/acquirer flow (Stripe and equivalents).
     * Requires the issuer's network to be reachable, which is why it is not the
     * default for a Tunisian merchant.
     */
    CARD,

    /** Cash on delivery. Nothing to authorise; money collected in person. */
    COD,

    /** Poste Tunisienne e-Dinar card / account. Domestic acquiring. */
    EDINAR,

    /** D17 wallet, La Poste Tunisienne. Domestic acquiring. */
    D17,

    /** ClicToPay bank cards, Société Monétique de Tunisie. */
    CLICTOPAY,

    /** A local mobile wallet (Konnect, Flouci, …). */
    WALLET,

    PAYPAL,
    BANK_TRANSFER;

    /**
     * True when a payment gateway must authorise and capture the payment.
     *
     * <p>Cash on delivery is the only method with no money to move up front — it
     * must skip the gateway entirely rather than being sent through it with a zero
     * amount.
     */
    public boolean requiresAuthorization() {
        return this != COD;
    }

    /**
     * True for instruments issued on Tunisian networks, which no international
     * acquirer will process. Routed to the local gateway.
     */
    public boolean isDomestic() {
        return this == EDINAR || this == D17 || this == CLICTOPAY || this == WALLET;
    }
}