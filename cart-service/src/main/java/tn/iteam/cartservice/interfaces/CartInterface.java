package tn.iteam.cartservice.interfaces;

import tn.iteam.cartservice.model.Cart;

import java.util.List;

public interface CartInterface {
    List<Cart> findByUserId(Long userId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
