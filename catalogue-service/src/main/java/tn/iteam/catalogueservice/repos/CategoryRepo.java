package tn.iteam.catalogueservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.catalogueservice.models.Category;

public interface CategoryRepo extends JpaRepository<Category, Long> {
    Category getCategoriesByName(String name);
}
