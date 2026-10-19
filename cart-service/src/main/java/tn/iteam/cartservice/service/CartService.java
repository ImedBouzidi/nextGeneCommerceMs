package tn.iteam.cartservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.cartservice.dto.AddToCartRequest;
import tn.iteam.cartservice.dto.ProductEventDTO;
import tn.iteam.cartservice.model.Cart;
import tn.iteam.cartservice.repos.CartRepository;
import tn.iteam.cartservice.util.SecurityUtils;
import tn.iteam.common.kafka.EventPublisher;
import tn.iteam.common.kafka.Topics;

import java.util.List;
@Slf4j
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final EventPublisher eventPublisher;

    public CartService(CartRepository cartRepository, EventPublisher eventPublisher) {
        this.cartRepository = cartRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Cart> getUserCart() {
        String userSub = SecurityUtils.getCurrentUserId();
        return cartRepository.findByUserId((userSub));
    }


    public Cart addToCart(AddToCartRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.error("User not authenticated");
            throw new RuntimeException("User not authenticated");
        }

        Cart cart = Cart.builder()
                .userId(userId)  // <-- now String
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build();

        Cart saved = cartRepository.save(cart);
        log.debug("Cart saved: {}", saved);

        // EventPublisher never throws and never blocks: a broker outage is logged
        // rather than failing the cart write that has already been committed.
        eventPublisher.publishJson(
                Topics.PRODUCT_EVENTS,
                ProductEventDTO.builder()
                        .productId(request.getProductId())
                        .userId(userId)  // keep as String
                        .eventType("PRODUCT_ADDED_TO_CART")
                        .quantity(request.getQuantity())
                        .build()
        );
        log.debug("Product event published for product {}", request.getProductId());

        return saved;
    }


}
