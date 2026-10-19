package tn.iteam.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import tn.iteam.orderservice.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDto {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date orderDate;
    private Long productId;
    private Long userId;
    private int qteOrdered;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
}
