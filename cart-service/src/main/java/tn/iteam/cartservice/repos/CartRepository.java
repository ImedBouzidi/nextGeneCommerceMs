package tn.iteam.cartservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.cartservice.model.Cart;

import java.util.List;

public interface CartRepository extends JpaRepository<Cart, Long> {
    List<Cart> findByUserId(String userId);
}
