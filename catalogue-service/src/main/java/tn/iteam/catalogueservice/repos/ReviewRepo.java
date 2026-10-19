package tn.iteam.catalogueservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.catalogueservice.models.Product;
import tn.iteam.catalogueservice.models.Review;

import java.util.List;

public interface ReviewRepo extends JpaRepository<Review, Long> {
    List<Review> findByProduct(Product product);
}
