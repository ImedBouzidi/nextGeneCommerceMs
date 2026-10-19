package tn.iteam.paymentservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tn.iteam.common.kafka.event.OrderCreatedEvent;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.paymentservice.dto.PaymentDto;
import tn.iteam.paymentservice.enums.PaymentStatus;
import tn.iteam.paymentservice.messaging.OrderSnapshotConsumer;
import tn.iteam.paymentservice.messaging.PaymentConfirmationConsumer;
import tn.iteam.paymentservice.messaging.PaymentRequestedConsumer;
import tn.iteam.paymentservice.model.OutboxEvent;
import tn.iteam.paymentservice.model.Payment;
import tn.iteam.paymentservice.repos.OrderSnapshotRepository;
import tn.iteam.paymentservice.repos.OutboxRepository;
import tn.iteam.paymentservice.repos.PaymentRepository;
import tn.iteam.paymentservice.repos.ProcessedEventRepository;
import tn.iteam.paymentservice.service.PaymentServiceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the behaviour that matters now that the charge is asynchronous:
 *
 * <ol>
 *   <li>a burst of simultaneous "pay" clicks still produces exactly one payment,</li>
 *   <li>a retry with the same idempotency key replays instead of charging again,</li>
 *   <li>the saga validates the order from the local read model, never from the request,</li>
 *   <li>a payment only becomes SUCCESS once stock has actually been reserved.</li>
 * </ol>
 *
 * <p>Kafka is not needed: the listeners are plain beans and are invoked directly, which
 * keeps these tests about the business rules rather than about broker plumbing.
 */
@SpringBootTest
class PaymentSagaTests {

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OrderSnapshotRepository snapshotRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderSnapshotConsumer orderSnapshotConsumer;

    @Autowired
    private PaymentRequestedConsumer paymentRequestedConsumer;

    @Autowired
    private PaymentConfirmationConsumer paymentConfirmationConsumer;

    @MockitoBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private static final Long ORDER_ID = 4242L;
    private static final Long PRODUCT_ID = 7L;
    private static final String USER_SUB = "user-1";
    /**
     * Order snapshots store the numeric user id, while the JWT carries a UUID subject;
     * the services derive one from the other. Tests must use the derived value or the
     * ownership check correctly rejects them.
     */
    private static final Long USER_ID = tn.iteam.paymentservice.util.UserIdentity.toUserId(USER_SUB);
    private static final int THREADS = 10;

    // ---------------------------------------------------------------- idempotency

    @Test
    void concurrentClicksForTheSameOrderProduceExactlyOnePayment() throws Exception {
        // These tests run against the configured database (no embedded DB in this
        // project), so fixtures are removed afterwards.
        PaymentDto request = PaymentDto.builder().orderId(ORDER_ID).build();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLine = new CountDownLatch(1);
        List<Future<PaymentDto>> results = new java.util.ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                startLine.await();                       // release all threads at once
                return paymentService.createPayment(request, USER_SUB, "key-" + ORDER_ID);
            }));
        }
        startLine.countDown();

        int answered = 0;
        for (Future<PaymentDto> f : results) {
            if (f.get(30, TimeUnit.SECONDS) != null) answered++;
        }
        pool.shutdown();

        assertThat(answered).isEqualTo(THREADS);
        assertThat(paymentRepository.findByOrderId(ORDER_ID))
                .as("only one payment row may exist for the order")
                .isPresent();
        assertThat(paymentRepository.findAll())
                .filteredOn(p -> ORDER_ID.equals(p.getOrderId()))
                .hasSize(1);
    }

    @Test
    void retryWithSameIdempotencyKeyReplaysTheOriginalPayment() {
        PaymentDto request = PaymentDto.builder().orderId(ORDER_ID).build();

        PaymentDto first = paymentService.createPayment(request, USER_SUB, "idem-abc");
        PaymentDto second = paymentService.createPayment(request, USER_SUB, "idem-abc");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getPaymentStatus()).isEqualTo(first.getPaymentStatus());
        assertThat(paymentRepository.findAll())
                .filteredOn(p -> ORDER_ID.equals(p.getOrderId()))
                .hasSize(1);
    }

    // ---------------------------------------------------------------------- saga

    @Test
    void acceptedPaymentStartsPendingAndStagesTheRequestEvent() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), USER_SUB, "saga-1");

        assertThat(created.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getAmount()).as("amount is resolved later, not guessed here").isNull();

        assertThat(outboxRepository.findAll())
                .anyMatch(e -> PaymentRequestedEvent.TYPE.equals(e.getEventType())
                        && e.getStatus().equals("NEW"));
    }

    @Test
    void paymentStaysPendingUntilStockIsReserved() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), USER_SUB, "saga-2");

        givenOrderSnapshot(ORDER_ID, USER_ID, new BigDecimal("42.00"), 2, "PENDING");

        // 1️⃣ the request is consumed: validated, priced from the order, stock asked for
        paymentRequestedConsumer.onPaymentRequested(consumeRequestEvent(created.getId()));

        Payment processing = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(processing.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(processing.getAmount()).isEqualByComparingTo("42.00");
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "stock.reserve".equals(e.getEventType()));

        // 2️⃣ still not paid: nothing has confirmed the stock yet
        assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getPaymentStatus())
                .isNotEqualTo(PaymentStatus.SUCCESS);

        // 3️⃣ catalogue confirms the reservation
        paymentConfirmationConsumer.onStockReserved(stockReservedEvent(processing));

        Payment paid = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.paid".equals(e.getEventType()));
    }

    @Test
    void failedReservationFailsThePaymentAndUnwindsTheOrder() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), USER_SUB, "saga-3");
        givenOrderSnapshot(ORDER_ID, USER_ID, new BigDecimal("10.00"), 1, "PENDING");

        paymentRequestedConsumer.onPaymentRequested(consumeRequestEvent(created.getId()));

        tn.iteam.common.kafka.event.StockReservationFailedEvent failure =
                new tn.iteam.common.kafka.event.StockReservationFailedEvent();
        failure.setOrderId(ORDER_ID);
        failure.setProductId(PRODUCT_ID);
        failure.setRequestedQuantity(5);
        failure.setAvailableQuantity(0);
        failure.setReason("Insufficient stock for product " + PRODUCT_ID + ": requested 5, available 0");

        paymentConfirmationConsumer.onStockReservationFailed(failure);

        Payment failed = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("Insufficient stock");
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "payment.failed".equals(e.getEventType()));
    }

    @Test
    void paymentForSomebodyElsesOrderIsRejected() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), "attacker-sub", "saga-4");
        // Snapshot belongs to a different user than the caller claims to be.
        givenOrderSnapshot(ORDER_ID, 12345L, new BigDecimal("10.00"), 1, "PENDING");

        PaymentRequestedEvent event = consumeRequestEvent(created.getId());
        event.setUserId("attacker-sub");

        paymentRequestedConsumer.onPaymentRequested(event);

        Payment payment = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("does not belong");
    }

    @Test
    void requestArrivingBeforeItsOrderSnapshotIsRetriedRatherThanPriced() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), USER_SUB, "saga-5");
        // No snapshot yet — the order.created event has not been consumed.
        PaymentRequestedEvent event = consumeRequestEvent(created.getId());

        assertThatThrownBy(() -> paymentRequestedConsumer.onPaymentRequested(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not in the local read model");

        assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getAmount())
                .as("nothing may be charged from client-supplied data")
                .isNull();
    }

    @Test
    void duplicateConfirmationEventDoesNotChargeTwice() {
        PaymentDto created = paymentService.createPayment(
                PaymentDto.builder().orderId(ORDER_ID).build(), USER_SUB, "saga-6");
        givenOrderSnapshot(ORDER_ID, USER_ID, new BigDecimal("10.00"), 1, "PENDING");
        paymentRequestedConsumer.onPaymentRequested(consumeRequestEvent(created.getId()));

        Payment processing = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        var reserved = stockReservedEvent(processing);

        paymentConfirmationConsumer.onStockReserved(reserved);
        long afterFirst = outboxRepository.findAll().stream()
                .filter(e -> "payment.paid".equals(e.getEventType())).count();

        paymentConfirmationConsumer.onStockReserved(reserved);   // Kafka redelivery
        long afterSecond = outboxRepository.findAll().stream()
                .filter(e -> "payment.paid".equals(e.getEventType())).count();

        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    // ------------------------------------------------------------------- fixtures

    private void givenOrderSnapshot(Long orderId, Long userId, BigDecimal total, int qty, String status) {
        OrderCreatedEvent created = new OrderCreatedEvent();
        created.setOrderId(orderId);
        created.setUserId(userId);
        created.setProductId(PRODUCT_ID);
        created.setQtyOrdered(qty);
        created.setTotalAmount(total);
        created.setOrderStatus(status);
        orderSnapshotConsumer.onOrderCreated(created);
    }

    /** Rebuilds the event the outbox holds, as the listener would receive it. */
    private PaymentRequestedEvent consumeRequestEvent(Long paymentId) {
        OutboxEvent staged = outboxRepository.findAll().stream()
                .filter(e -> PaymentRequestedEvent.TYPE.equals(e.getEventType()))
                .filter(e -> paymentId.toString().equals(e.getAggregateId()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no payment.requested staged for payment " + paymentId));

        PaymentRequestedEvent event = new PaymentRequestedEvent();
        event.setEventId(staged.getEventId());
        event.setOrderId(ORDER_ID);
        event.setUserId(USER_SUB);
        event.setPaymentMethod("COD");
        return event;
    }

    private tn.iteam.common.kafka.event.StockReservedEvent stockReservedEvent(Payment payment) {
        var reserved = new tn.iteam.common.kafka.event.StockReservedEvent();
        reserved.setEventId("stock-reserved-" + payment.getId());
        reserved.setOrderId(payment.getOrderId());
        reserved.setProductId(PRODUCT_ID);
        reserved.setQuantity(1);
        reserved.setRemainingQuantity(9);
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