package tn.iteam.cartservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartRequest {
    private Long productId;
    private Integer quantity;
}
