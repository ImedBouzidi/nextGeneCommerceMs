package tn.iteam.catalogueservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tn.iteam.catalogueservice.dto.CategoryDto;
import tn.iteam.common.kafka.EventPublisher;
import tn.iteam.common.kafka.Topics;
import tn.iteam.catalogueservice.interfaces.ICategory;
import tn.iteam.catalogueservice.mappers.CatMapper;
import tn.iteam.catalogueservice.models.Category;
import tn.iteam.catalogueservice.repos.CategoryRepo;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService implements ICategory {

    private final CategoryRepo categoryRepo;
    private final CatMapper mapper;
    private final EventPublisher eventPublisher;
    @Override
    public CategoryDto getCategory(Long id) {
        Category category = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return mapper.toDto(category);
    }

    @Override
    public CategoryDto getCategoryByName(String name) {
        return mapper.toDto(categoryRepo.getCategoriesByName(name));
    }

    @Override
    public List<CategoryDto> getCategories() {
        return categoryRepo.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public CategoryDto addCategory(CategoryDto dto) {
        Category category = mapper.toEntity(dto);
        Category saved = categoryRepo.save(category);
        // Publish Kafka event after saving; never fail the write if the broker is down
        eventPublisher.publish(Topics.CATEGORY_EVENTS, "New category created: " + saved.getName());
        return mapper.toDto(saved);
    }

    @Override
    public void removeCategory(Long categoryId) {
        categoryRepo.deleteById(categoryId);
        eventPublisher.publish(Topics.CATEGORY_EVENTS, "Category deleted with id: " + categoryId);
    }

    @Override
    public CategoryDto updateCategory(Long id, CategoryDto dto) {
        Category category = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        Category updated = categoryRepo.save(category);
        eventPublisher.publish(Topics.CATEGORY_EVENTS, "Category updated: " + updated.getName());
        return mapper.toDto(updated);
    }

    @Override
    public String getCurrentConnectedUser() {
        return "";
    }
}
