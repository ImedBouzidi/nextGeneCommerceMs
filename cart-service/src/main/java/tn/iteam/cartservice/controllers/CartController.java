package tn.iteam.cartservice.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.iteam.cartservice.model.Cart;
import tn.iteam.cartservice.service.CartService;
import tn.iteam.cartservice.dto.AddToCartRequest;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/myCart")
    public ResponseEntity<List<Cart>> getCart() {
        List<Cart> cart = cartService.getUserCart();
        log.debug("Retrieved cart for user: {}", cart);
        return ResponseEntity.ok(cart);
    }

    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/new")
    public ResponseEntity<Cart> addToCart(@RequestBody AddToCartRequest request) {
        try {
            Cart cart = cartService.addToCart(request);
            log.debug("Added item to cart: {}", cart);
            return ResponseEntity.ok(cart);
        } catch (Exception e) {
            log.error("Error adding item to cart", e);
            return ResponseEntity.status(500).build();
        }
    }
}
