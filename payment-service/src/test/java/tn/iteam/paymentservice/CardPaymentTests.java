package tn.iteam.paymentservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tn.iteam.common.kafka.event.OrderCreatedEvent;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.common.kafka.event.StockReservationFailedEvent;
import tn.iteam.common.kafka.event.StockReservedEvent;
import tn.iteam.paymentservice.dto.PaymentDto;
import tn.iteam.paymentservice.enums.PaymentMethod;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.exceptions.BusinessException;
import tn.iteam.paymentservice.gateway.FakePaymentGateway;
import tn.iteam.paymentservice.gateway.PaymentGateway;
import tn.iteam.paymentservice.messaging.OrderSnapshotConsumer;
import tn.iteam.paymentservice.messaging.PaymentConfirmationConsumer;
import tn.iteam.paymentservice.messaging.PaymentRequestedConsumer;
import tn.iteam.paymentservice.model.OutboxEvent;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.outbox.PaymentReconciliationJob;
import tn.iteam.paymentservice.repos.OrderSnapshotRepository;
import tn.iteam.paymentservice.repos.OutboxRepository;
import tn.iteam.paymentservice.repos.PaymentRepository;
import tn.iteam.paymentservice.repos.ProcessedEventRepository;
import tn.iteam.paymentservice.service.PaymentServiceImpl;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tier 1 tests: every money path that a real card can take, driven deterministically
 * through {@link FakePaymentGateway}.
 *
 * <p>These are the cases that cannot be provoked reliably against a sandbox and
 * impossible against a fake HTTP stub: an issuer that declines, a card that needs
 * 3-D Secure, an acquirer that dies mid-transaction, a capture that is refused after
 * stock is already reserved, and a redelivered webhook.
 *
 * <p>Run with the default fake gateways — no keys, no network.
 */
@SpringBootTest
class CardPaymentTests {

    @Autowired private PaymentServiceImpl paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OrderSnapshotRepository snapshotRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private OrderSnapshotConsumer orderSnapshotConsumer;
    @Autowired private PaymentRequestedConsumer paymentRequestedConsumer;
    @Autowired private PaymentConfirmationConsumer paymentConfirmationConsumer;
    @Autowired private tn.iteam.paymentservice.gateway.PaymentGatewaySelector selector;

    /** The gateway a card payment is routed to in these tests. */
    private PaymentGateway gateway() {
        return selector.forMethod(PaymentMethod.CARD);
    }
    @Autowired private PaymentReconciliationJob reconciliationJob;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private static final Long ORDER_ID = 5150L;
    private static final Long PRODUCT_ID = 11L;
    private static final String USER_SUB = "card-tester";
    private static final Long USER_ID = tn.iteam.paymentservice.util.UserIdentity.toUserId(USER_SUB);

    // ------------------------------------------------------------------ happy path

    @Test
    void cardPaymentIsAuthorisedThenCapturedOnlyAfterStockIsReserved() {
        PaymentDto accepted = createCardPayment("card-ok", new BigDecimal("120.00"));

        givenOrderSnapshot(new BigDecimal("120.00"), 2, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        Payment held = payment(ORDER_ID);
        assertThat(held.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(held.getAuthorizationId()).as("funds must be held").isNotBlank();
        assertThat(held.getCapturedAt()).as("not captured before stock is confirmed").isNull();
        assertThat(held.getCurrency()).isEqualTo("TND");
        assertThat(held.getGateway()).isEqualTo("fake");

        // Stock confirms → now the money is taken.
        paymentConfirmationConsumer.onStockReserved(stockReserved("evt-ok"));

        Payment paid = payment(ORDER_ID);
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.getCapturedAt()).isNotNull();
        assertThat(paid.getClientSecret()).as("challenge secret is dead once captured").isNull();
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.paid".equals(e.getEventType()));
    }

    // ------------------------------------------------------------------- declines

    @Test
    void insufficientFundsFailsThePaymentBeforeAnyStockIsReserved() {
        PaymentDto accepted = createCardPayment("card-nsf", new BigDecimal("0.01"));
        givenOrderSnapshot(new BigDecimal("0.01"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        Payment failed = payment(ORDER_ID);
        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("insufficient_funds");
        assertThat(failed.getAuthorizationId()).isNull();
        assertThat(outboxRepository.findAll())
                .as("stock must not be touched when the card is declined")
                .noneMatch(e -> "stock.reserve".equals(e.getEventType()));
    }

    @Test
    void genericDeclineIsRecordedWithItsReason() {
        PaymentDto accepted = createCardPayment("card-decl", new BigDecimal("0.02"));
        givenOrderSnapshot(new BigDecimal("0.02"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment(ORDER_ID).getFailureCode()).isEqualTo("card_declined");
    }

    // ------------------------------------------------- challenge / 3-D Secure path

    @Test
    void requiresActionKeepsTheHoldAndHandsBackAClientSecret() {
        PaymentDto accepted = createCardPayment("card-3ds", new BigDecimal("0.03"));
        givenOrderSnapshot(new BigDecimal("0.03"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        Payment challenging = payment(ORDER_ID);
        assertThat(challenging.getAuthorizationId()).isNotBlank();
        assertThat(challenging.getClientSecret())
                .as("frontend needs this to finish the challenge")
                .isNotBlank();
        assertThat(challenging.getCapturedAt()).isNull();
    }

    // ---------------------------------------------------------- acquirer outage

    @Test
    void providerOutageRetriesInsteadOfFailingTheCustomer() {
        PaymentDto accepted = createCardPayment("card-down", new BigDecimal("0.04"));
        givenOrderSnapshot(new BigDecimal("0.04"), 1, "PENDING");

        // Throwing is what makes the Kafka error handler redeliver. Failing here
        // would tell the customer their card was refused when the acquirer was simply
        // unreachable — and would leave an order nobody can pay for.
        assertThatThrownBy(() -> paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");

        Payment untouched = payment(ORDER_ID);
        assertThat(untouched.getPaymentStatus()).as("must stay retryable").isEqualTo(PaymentStatus.PENDING);
        assertThat(outboxRepository.findAll())
                .noneMatch(e -> "stock.reserve".equals(e.getEventType()));
    }

    @Test
    void retryAfterOutageSucceedsWithoutASecondHold() {
        // Amount now authorises normally; the same idempotency key must be honoured.
        PaymentDto accepted = createCardPayment("card-retry", new BigDecimal("60.00"));
        givenOrderSnapshot(new BigDecimal("60.00"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        String firstAuthorization = payment(ORDER_ID).getAuthorizationId();

        // Redelivery of the same event must be a no-op, not a second authorisation.
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        assertThat(payment(ORDER_ID).getAuthorizationId()).isEqualTo(firstAuthorization);
        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    // ------------------------------------------------- compensation / money safety

    @Test
    void stockFailureCancelsTheHoldSoTheCustomerIsNeverCharged() {
        PaymentDto accepted = createCardPayment("card-nostock", new BigDecimal("75.00"));
        givenOrderSnapshot(new BigDecimal("75.00"), 2, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        assertThat(payment(ORDER_ID).getAuthorizationId()).isNotBlank();

        StockReservationFailedEvent failure = new StockReservationFailedEvent();
        failure.setEventId("evt-nostock");
        failure.setOrderId(ORDER_ID);
        failure.setProductId(PRODUCT_ID);
        failure.setRequestedQuantity(2);
        failure.setAvailableQuantity(0);
        failure.setReason("Insufficient stock");

        paymentConfirmationConsumer.onStockReservationFailed(failure);

        Payment failed = payment(ORDER_ID);
        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getCapturedAt()).as("never captured → nothing to refund").isNull();
        assertThat(failed.getFailureReason()).isEqualTo("Insufficient stock");
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.failed".equals(e.getEventType()));
        assertThat(outboxRepository.findAll())
                .noneMatch(e -> "payment.paid".equals(e.getEventType()));
    }

    @Test
    void captureRefusedAfterStockReservedUnwindsInsteadOfClaimingSuccess() {
        // Amount 0.05 authorises but refuses capture: the nastiest real-world case,
        // because stock is already committed when the bank says no.
        PaymentDto accepted = createCardPayment("card-nocapture", new BigDecimal("0.05"));
        givenOrderSnapshot(new BigDecimal("0.05"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);

        paymentConfirmationConsumer.onStockReserved(stockReserved("evt-nocapture"));

        Payment payment = payment(ORDER_ID);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getCapturedAt()).isNull();
        assertThat(outboxRepository.findAll())
                .as("an uncaptured payment must never be announced as paid")
                .noneMatch(e -> "payment.paid".equals(e.getEventType()));
    }

    // ------------------------------------------------------------- webhook replay

    @Test
    void duplicateStockConfirmationNeverCapturesOrChargesTwice() {
        PaymentDto accepted = createCardPayment("card-dup", new BigDecimal("30.00"));
        givenOrderSnapshot(new BigDecimal("30.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        StockReservedEvent reserved = stockReserved("evt-dup");
        paymentConfirmationConsumer.onStockReserved(reserved);
        long paidEventsAfterFirst = countEvents("payment.paid");
        java.time.LocalDateTime capturedAt = payment(ORDER_ID).getCapturedAt();

        paymentConfirmationConsumer.onStockReserved(reserved);   // Stripe/Kafka redelivery

        assertThat(countEvents("payment.paid")).isEqualTo(paidEventsAfterFirst);
        assertThat(payment(ORDER_ID).getCapturedAt()).isEqualTo(capturedAt);
    }

    // ------------------------------------------------- simulated issuer outcomes

    // ------------------------------------------------- simulated issuer outcomes

    @Test
    void simulatedChallengeKeepsTheHoldPendingAuthentication() {
        PaymentDto accepted = createCardPayment("sim-3ds", new BigDecimal("45.00"));
        givenOrderSnapshot(new BigDecimal("45.00"), 1, "PENDING");

        PaymentRequestedEvent event = requestEventFor(accepted);
        event.setSimulatedOutcome("REQUIRES_ACTION");
        paymentRequestedConsumer.onPaymentRequested(event);

        Payment challenging = payment(ORDER_ID);
        assertThat(challenging.getClientSecret())
                .as("the frontend needs this to finish the challenge")
                .isNotBlank();
        assertThat(challenging.getAuthorizationId()).isNotBlank();
        assertThat(challenging.getCapturedAt())
                .as("nothing is captured until the customer authenticates")
                .isNull();
        assertThat(challenging.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void simulatedProcessingErrorIsRetriedRatherThanFailed() {
        PaymentDto accepted = createCardPayment("sim-error", new BigDecimal("35.00"));
        givenOrderSnapshot(new BigDecimal("35.00"), 1, "PENDING");

        PaymentRequestedEvent event = requestEventFor(accepted);
        event.setSimulatedOutcome("PROCESSING_ERROR");
        assertThatThrownBy(() -> paymentRequestedConsumer.onPaymentRequested(event))
                .isInstanceOf(RuntimeException.class);

        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    /**
     * The browser maps Stripe's published test cards to these outcome names:
     * {@code 4242…4242 → AUTHORIZED}, {@code …9995 → INSUFFICIENT_FUNDS},
     * {@code …0002 → DECLINED}, {@code …0069 → EXPIRED_CARD}.
     */
    @Test
    void simulatedOutcomesDriveTheIssuerDecision() {
        assertSimulatedOutcome("AUTHORIZED", null);
        assertSimulatedOutcome("INSUFFICIENT_FUNDS", "insufficient_funds");
        assertSimulatedOutcome("DECLINED", "card_declined");
        assertSimulatedOutcome("EXPIRED_CARD", "expired_card");
    }

    /**
     * Runs one simulated card through the saga on a clean order.
     *
     * @param expectedFailureCode {@code null} means the authorisation succeeds.
     */
    private void assertSimulatedOutcome(String simulatedOutcome, String expectedFailureCode) {
        PaymentDto accepted = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).paymentMethod(PaymentMethod.CARD).build(),
                USER_SUB, "sim-" + simulatedOutcome);
        givenOrderSnapshot(new BigDecimal("40.00"), 1, "PENDING");

        PaymentRequestedEvent event = requestEventFor(accepted);
        event.setSimulatedOutcome(simulatedOutcome);
        paymentRequestedConsumer.onPaymentRequested(event);

        Payment payment = payment(ORDER_ID);
        if (expectedFailureCode == null) {
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(payment.getAuthorizationId()).isNotBlank();
        } else {
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo(expectedFailureCode);
            assertThat(payment.getAuthorizationId()).as("a declined card holds nothing").isNull();
        }

        // Clear the order so the next simulated card starts from a clean slate.
        paymentRepository.deleteAll();
        snapshotRepository.deleteAll();
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    // ---------------------------------------------------------------- refunds

    @Test
    void capturedPaymentCanBeRefunded() {
        PaymentDto accepted = createCardPayment("refund-ok", new BigDecimal("80.00"));
        givenOrderSnapshot(new BigDecimal("80.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        paymentConfirmationConsumer.onStockReserved(stockReserved("evt-refund-ok"));

        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        PaymentDto refunded = paymentService.refundPayment(payment(ORDER_ID).getId(), null, "admin");

        assertThat(refunded.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.refunded".equals(e.getEventType()));
    }

    @Test
    void refundIsRefusedWhileMoneyIsOnlyHeld() {
        PaymentDto accepted = createCardPayment("refund-held", new BigDecimal("55.00"));
        givenOrderSnapshot(new BigDecimal("55.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        // PROCESSING: the funds are authorised but never captured. Refunding would
        // create a charge and immediately reverse it.
        assertThatThrownBy(() -> paymentService.refundPayment(payment(ORDER_ID).getId(), null, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only a captured payment");
    }

    @Test
    void refundIsRefusedTwice() {
        PaymentDto accepted = createCardPayment("refund-twice", new BigDecimal("70.00"));
        givenOrderSnapshot(new BigDecimal("70.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        paymentConfirmationConsumer.onStockReserved(stockReserved("evt-refund-twice"));

        Long id = payment(ORDER_ID).getId();
        paymentService.refundPayment(id, null, "admin");

        assertThatThrownBy(() -> paymentService.refundPayment(id, null, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been refunded");
    }

    // ---------------------------------------------------------- reconciliation

    @Test
    void reconciliationReleasesAPaymentThatStalledWithMoneyHeld() {
        PaymentDto accepted = createCardPayment("stuck", new BigDecimal("65.00"));
        givenOrderSnapshot(new BigDecimal("65.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));
        assertThat(payment(ORDER_ID).getAuthorizationId()).as("money is held").isNotBlank();

        // Backdate past the job's threshold. @PreUpdate would overwrite updatedAt on
        // save, so the row has to be aged with SQL.
        jdbcTemplate.update("UPDATE payments SET updated_at = now() - interval '2 hours' WHERE order_id = ?",
                ORDER_ID);

        reconciliationJob.releaseStuckPayments();

        Payment released = payment(ORDER_ID);
        assertThat(released.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(released.getFailureCode()).isEqualTo("saga_timeout");
        assertThat(released.getCapturedAt()).as("never captured").isNull();
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.failed".equals(e.getEventType()));
    }

    @Test
    void reconciliationLeavesHealthyPaymentsAlone() {
        PaymentDto accepted = createCardPayment("healthy", new BigDecimal("20.00"));
        givenOrderSnapshot(new BigDecimal("20.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(requestEventFor(accepted));

        // Freshly updated — well inside the grace period.
        reconciliationJob.releaseStuckPayments();

        assertThat(payment(ORDER_ID).getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    // ------------------------------------------------------------------- helpers

    private long countEvents(String type) {
        return outboxRepository.findAll().stream().filter(e -> type.equals(e.getEventType())).count();
    }

    /**
     * Creates the payment through the real service, so the unique constraint and the
     * outbox event are produced exactly as in production. The amount is decided
     * server-side from the order snapshot; it is only needed by the fake gateway to
     * decide which scripted outcome to return, so it is set up in the snapshot by
     * {@link #givenOrderSnapshot} before the listener runs.
     */
    private PaymentDto createCardPayment(String key, BigDecimal amount) {
        return paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).paymentMethod(PaymentMethod.CARD).build(),
                USER_SUB, key);
    }

    private Payment payment(Long orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    private void givenOrderSnapshot(BigDecimal total, int qty, String status) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(ORDER_ID);
        event.setUserId(USER_ID);
        event.setProductId(PRODUCT_ID);
        event.setQtyOrdered(qty);
        event.setTotalAmount(total);
        event.setOrderStatus(status);
        orderSnapshotConsumer.onOrderCreated(event);
    }

    private PaymentRequestedEvent requestEventFor(PaymentDto dto) {
        OutboxEvent staged = outboxRepository.findAll().stream()
                .filter(e -> "payment.requested".equals(e.getEventType()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no payment.requested staged"));

        PaymentRequestedEvent event = new PaymentRequestedEvent();
        event.setEventId(staged.getEventId());
        event.setOrderId(dto.getOrderId());
        event.setUserId(USER_SUB);
        event.setPaymentMethod("CARD");
        return event;
    }

    private StockReservedEvent stockReserved(String eventId) {
        StockReservedEvent reserved = new StockReservedEvent();
        reserved.setEventId(eventId);
        reserved.setOrderId(ORDER_ID);
        reserved.setProductId(PRODUCT_ID);
        reserved.setQuantity(1);
        reserved.setRemainingQuantity(3);
        return reserved;
    }

    @AfterEach
    void cleanUp() {
        processedEventRepository.deleteAll();
        outboxRepository.deleteAll();
        snapshotRepository.deleteAll();
        paymentRepository.findAll().stream()
                .filter(p -> ORDER_ID.equals(p.getOrderId()))
                .forEach(paymentRepository::delete);
    }
}