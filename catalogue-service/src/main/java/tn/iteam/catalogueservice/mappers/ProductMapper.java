package tn.iteam.catalogueservice.mappers;

import org.springframework.stereotype.Component;
import tn.iteam.catalogueservice.dto.ProductDto;
import tn.iteam.catalogueservice.models.Product;

@Component
public class ProductMapper {

    public ProductDto toDto(Product product) {
        if (product == null) return null;

        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .rate(product.getRate())
                .review(product.getReview())
                .images(product.getImages())
                .quantity(product.getQuantity())
                .categoryId(
                        product.getCategory() != null ? product.getCategory().getId() : null
                )
                .categoryName(
                        product.getCategory() != null ? product.getCategory().getName() : null
                )
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public Product toEntity(ProductDto dto) {
        if (dto == null) return null;

        Product product = new Product();
        product.setId(dto.getId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setRate(dto.getRate());
        product.setReview(dto.getReview());
        product.setImages(dto.getImages());
        product.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : product.getCreatedAt());
        product.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : product.getUpdatedAt());
        product.setQuantity(dto.getQuantity());

        // 🟡 Category is set later in the service layer
        return product;
    }
}
