package tn.iteam.orderservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.orderservice.model.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}