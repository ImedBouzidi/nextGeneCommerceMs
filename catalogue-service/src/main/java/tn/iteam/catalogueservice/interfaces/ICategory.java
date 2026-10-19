package tn.iteam.catalogueservice.interfaces;

import tn.iteam.catalogueservice.dto.CategoryDto;
import java.util.List;

public interface ICategory {
    CategoryDto getCategory(Long id);
    CategoryDto getCategoryByName(String name);
    List<CategoryDto> getCategories();
    CategoryDto addCategory(CategoryDto category);
    void removeCategory(Long categoryId);
    CategoryDto updateCategory(Long id, CategoryDto category);
    // extra: get current connected user
    String getCurrentConnectedUser();
}

