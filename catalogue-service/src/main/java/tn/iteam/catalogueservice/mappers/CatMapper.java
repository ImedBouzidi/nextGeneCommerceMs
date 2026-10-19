package tn.iteam.catalogueservice.mappers;

import org.springframework.stereotype.Component;
import tn.iteam.catalogueservice.dto.CategoryDto;
import tn.iteam.catalogueservice.models.Category;

@Component
public class CatMapper {
    public CategoryDto toDto(Category category) {
        if (category == null) return null;
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }

    public Category toEntity(CategoryDto dto) {
        if (dto == null) return null;
        return Category.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }
}
