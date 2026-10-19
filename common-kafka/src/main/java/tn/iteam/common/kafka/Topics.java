package tn.iteam.common.kafka;

/**
 * Canonical topic names.
 *
 * <p>The authoritative topic definitions (partitions, replication) live in
 * kafka-service's {@code KafkaConfig}. These constants exist so producers and
 * consumers cannot drift apart on spelling.
 *
 * <p>The payment/inventory topics follow a choreography: nothing in the platform
 * calls another service's REST API to move an order or adjust stock, the events
 * below carry the intent and each service reacts.
 */
public final class Topics {

    // auth
    public static final String USER_REGISTERED = "user-registered";

    // catalogue
    public static final String CATEGORY_EVENTS = "category-events";
    public static final String PRODUCT_EVENTS = "product-events";

    // order
    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String ORDER_APPROVED = "order-approved";

    /** Human-readable notification kept for dashboards; not part of the saga. */
    public static final String PAYMENT_STATUS_UPDATED = "payment-status-updated";

    // ------------------------------------------------------------------ payment
    /**
     * Command: "charge this order". Emitted by payment-service into its own outbox
     * and consumed by payment-service itself.
     *
     * <p>Being consumed by the producer looks odd, but it is what makes the payment
     * asynchronous: the HTTP request only records intent and returns; validation,
     * stock reservation and the final status all happen off the request thread.
     */
    public static final String PAYMENT_REQUESTED = "payment.requested";

    /** Fact: stock is reserved and the money is captured. Consumed by order-service. */
    public static final String PAYMENT_PAID = "payment.paid";

    /** Fact: the payment could not be completed. Consumed by order-service. */
    public static final String PAYMENT_FAILED = "payment.failed";

    // --------------------------------------------------------------- inventory
    /** Command: reserve N units of a product. Consumed by catalogue-service. */
    public static final String STOCK_RESERVE = "stock.reserve";

    /** Fact: reservation succeeded. Consumed by payment-service to finalise. */
    public static final String STOCK_RESERVED = "stock.reserved";

    /** Fact: reservation rejected (insufficient stock). Undoes the order. */
    public static final String STOCK_RESERVATION_FAILED = "stock.reservation-failed";

    // -------------------------------------------------------- dead letter suffix
    /** Events that could not be processed after all retries land on {@code <topic>.DLT}. */
    public static final String DLT_SUFFIX = ".DLT";

    public static String deadLetter(String topic) {
        return topic + DLT_SUFFIX;
    }

    private Topics() {
    }
}