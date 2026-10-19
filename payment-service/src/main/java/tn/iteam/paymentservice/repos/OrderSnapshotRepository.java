package tn.iteam.paymentservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.paymentservice.model.OrderSnapshot;

import java.util.Optional;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {

    Optional<OrderSnapshot> findByOrderId(Long orderId);
}