package tn.iteam.catalogueservice.mappers;

import org.springframework.stereotype.Component;
import tn.iteam.catalogueservice.dto.ReviewDto;
import tn.iteam.catalogueservice.models.Review;

@Component
public class ReviewMapper {

    public ReviewDto toDto(Review review) {
        if (review == null) return null;

        return ReviewDto.builder()
                .id(review.getId())
                .comment(review.getComment())
                .rating(review.getRating())
                //.reviewerName(review.getReviewerName())
                .productId(
                        review.getProduct() != null ? review.getProduct().getId() : null
                )
                .productName(
                        review.getProduct() != null ? review.getProduct().getName() : null
                )
                .createdAt(review.getCreatedAt())
                .build();
    }

    public Review toEntity(ReviewDto dto) {
        if (dto == null) return null;

        Review review = new Review();
        review.setId(dto.getId());
        review.setComment(dto.getComment());
        review.setRating(dto.getRating());
        //review.setReviewerName(dto.getReviewerName());
        review.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : review.getCreatedAt());

        // 🟡 Product will be set in the service layer using dto.getProductId()
        return review;
    }
}
