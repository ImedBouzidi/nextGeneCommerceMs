package tn.iteam.orderservice.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.iteam.orderservice.dto.CreateOrderRequest;
import tn.iteam.orderservice.dto.OrderResponseDto;
import tn.iteam.orderservice.enums.PaymentStatus;
import tn.iteam.orderservice.model.Order;
import tn.iteam.orderservice.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(@RequestHeader("Authorization") String authHeader) {
        List<Order> orders = orderService.getMyOrders(authHeader);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/place-order")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Order> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid CreateOrderRequest orderRequest) {

        Order saved = orderService.placeOrder(authHeader, orderRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {

        orderService.cancelOrder(authHeader, orderId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<Order>> getAllOrders(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getAllOrders(authHeader));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{orderId}/approve")
    public ResponseEntity<Order> approveOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {

        Order approvedOrder = orderService.approveOrder(authHeader, orderId, force);
        return ResponseEntity.ok(approvedOrder);
    }

    // Endpoint for payment service to update payment status (or for testing)
    @PutMapping("/{orderId}/payment-status")
    public ResponseEntity<Order> updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam PaymentStatus paymentStatus) {

        Order order = orderService.updatePaymentStatus(orderId, paymentStatus);
        return ResponseEntity.ok(order);
    }

    //@PreAuthorize("hasAuthority('SCOPE_order:read')")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable Long orderId) {

        Order order = orderService.findById(orderId);

        OrderResponseDto dto = OrderResponseDto.builder()
                .orderId(order.getId())
                .productId(order.getProductId())
                .qteOrdered(order.getQteOrdered())
                .amount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus().name())
                // payment-service checks this to refuse paying for somebody else's
                // order; without it the ownership check has to be skipped.
                .userId(order.getUserId())
                .mobile(order.getMobile())
                .paymentMethod(order.getPaymentMethod())
                .build();

        return ResponseEntity.ok(dto);
    }

}
