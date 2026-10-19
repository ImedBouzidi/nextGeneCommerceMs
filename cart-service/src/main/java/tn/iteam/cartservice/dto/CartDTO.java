package tn.iteam.cartservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDTO {
    private Long id;
    private String userId;
    private Long productId;
    private Integer quantity;
}
