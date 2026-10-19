package tn.iteam.orderservice.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.orderservice.enums.OrderStatus;
import tn.iteam.orderservice.enums.PaymentStatus;
import tn.iteam.orderservice.model.Order;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
