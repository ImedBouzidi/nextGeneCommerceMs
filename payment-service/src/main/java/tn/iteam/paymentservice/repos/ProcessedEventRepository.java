package tn.iteam.paymentservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.paymentservice.model.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}