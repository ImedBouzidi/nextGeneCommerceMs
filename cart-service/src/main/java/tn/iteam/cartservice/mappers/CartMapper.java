package tn.iteam.cartservice.mappers;

import org.springframework.stereotype.Component;
import tn.iteam.cartservice.dto.CartDTO;
import tn.iteam.cartservice.model.Cart;

@Component
public class CartMapper {
    public CartDTO toDto(Cart cart) {
        return CartDTO.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .productId(cart.getProductId())
                .quantity(cart.getQuantity())
                .build();
    }

    public Cart toEntity(CartDTO dto) {
        Cart cart = new Cart();
        cart.setId(dto.getId());
        cart.setUserId(dto.getUserId());
        cart.setProductId(dto.getProductId());
        cart.setQuantity(dto.getQuantity());
        return cart;
    }
}
