package tn.iteam.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.iteam.paymentservice.dto.PaymentDto;
import tn.iteam.paymentservice.exceptions.BusinessException;
import tn.iteam.paymentservice.interfaces.PaymentService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    /**
     * Header carrying the caller's idempotency key. A retry of the same logical
     * payment (double click, network retry, page reload) reuses it and gets the
     * original payment back instead of creating a second charge.
     */
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PaymentService paymentService;

    /**
     * Accepts a payment request.
     *
     * <p>Returns {@code 202 Accepted} with a PENDING payment: the order has been
     * claimed and the charge is now being driven over Kafka. The client polls
     * {@code GET /api/payments/by-order/{orderId}} until the status becomes SUCCESS or
     * FAILED.
     *
     * <p>A repeat of the same {@code Idempotency-Key} (double click, reload, network
     * retry) returns the original payment instead of charging again.
     */
    @PostMapping
    public ResponseEntity<PaymentDto> create(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody PaymentDto dto,
            @AuthenticationPrincipal Jwt jwt) {

        PaymentDto payment = paymentService.createPayment(dto, subject(jwt), idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(payment);
    }

    /**
     * Owner-or-admin: reading somebody else's payment returns 403 rather than 404 so
     * the response does not confirm that the id exists.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentService.getPaymentForUser(id, subject(jwt)));
    }

    /** Polling endpoint for the checkout flow, while the saga resolves. */
    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<PaymentDto> getByOrder(@PathVariable Long orderId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderForUser(orderId, subject(jwt)));
    }

    /** The caller's own payments. */
    @GetMapping("/mine")
    public ResponseEntity<List<PaymentDto>> getMine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentService.getPaymentsForUser(subject(jwt)));
    }

    /** Full transaction list — the previous version exposed every customer's payments to any logged-in user. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<PaymentDto>> getAll() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<PaymentDto> update(@PathVariable Long id, @RequestBody PaymentDto dto) {
        return ResponseEntity.ok(paymentService.updatePayment(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns captured money to the customer.
     *
     * <p>Admin only, and refused unless the payment was actually captured — see
     * {@code PaymentServiceImpl#refundPayment} for why refunding a mere
     * authorisation would be wrong. Optional {@code amount} for a partial refund;
     * omitted means the full amount.
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<PaymentDto> refund(@PathVariable Long id,
                                             @RequestParam(required = false) BigDecimal amount,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentService.refundPayment(id, amount, subject(jwt)));
    }

    private String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException("Missing authenticated subject", HttpStatus.UNAUTHORIZED);
        }
        return jwt.getSubject();
    }
}