package tn.iteam.catalogueservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.catalogueservice.models.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}