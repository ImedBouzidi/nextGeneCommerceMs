package tn.iteam.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import tn.iteam.orderservice.enums.OrderStatus;
import tn.iteam.orderservice.enums.PaymentStatus;

import java.util.Date;  // Changed from LocalDateTime to Date

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int qteOrdered;
    @Temporal(TemporalType.TIMESTAMP)  // Explicit for JPA to map Date to datetime
    private Date orderDate;  // Reformulated as Date (serializes to ISO string in JSON)
    private Long productId;
    private Long userId;
    private double TotalAmount;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private Long approvedBy; // Admin user ID who approved the order
    private Date approvedAt; // Timestamp when order was approved

    private String mobile;
    private String paymentMethod;
}