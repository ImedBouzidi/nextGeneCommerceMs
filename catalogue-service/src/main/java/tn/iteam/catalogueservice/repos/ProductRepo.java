package tn.iteam.catalogueservice.repos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.iteam.catalogueservice.models.Product;

import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product, Long> {

    boolean existsByName(String name);

    /**
     * Reads a product for update.
     *
     * <p>The previous {@code decreaseQuantity} did a plain findById followed by a
     * save. Two concurrent reservations for the last unit both read quantity=1, both
     * decided there was stock, and both wrote 0 — overselling by one. Taking the row
     * lock in the SELECT serialises them so the second sees quantity=0 and is refused.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}