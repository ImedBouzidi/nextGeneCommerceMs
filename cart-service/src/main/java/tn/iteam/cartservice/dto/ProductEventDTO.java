package tn.iteam.cartservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEventDTO {
    private Long productId;
    private String eventType; // e.g., "PRODUCT_ADDED_TO_CART"
    private String userId;
    private Integer quantity;
}
