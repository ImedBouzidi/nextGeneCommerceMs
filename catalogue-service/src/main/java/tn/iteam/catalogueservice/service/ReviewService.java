package tn.iteam.catalogueservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import tn.iteam.catalogueservice.dto.ReviewDto;
import tn.iteam.catalogueservice.mappers.ReviewMapper;
import tn.iteam.catalogueservice.models.Product;
import tn.iteam.catalogueservice.models.Review;
import tn.iteam.catalogueservice.repos.ProductRepo;
import tn.iteam.catalogueservice.repos.ReviewRepo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepo reviewRepo;
    private final ProductRepo productRepo;
    private final ReviewMapper reviewMapper;

    public ReviewService(ReviewRepo reviewRepo, ProductRepo productRepo, ReviewMapper reviewMapper) {
        this.reviewRepo = reviewRepo;
        this.productRepo = productRepo;
        this.reviewMapper = reviewMapper;
    }

    /**
     * Add a review for a given product and update product rating automatically.
     */


    @Transactional
    public Review addReview(Long productId, ReviewDto dto) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Review review = reviewMapper.toEntity(dto);
        review.setProduct(product);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getSubject();  // Keycloak user ID (sub claim)
            String username = jwtAuth.getToken().getClaimAsString("preferred_username");  // Keycloak username
            review.setReviewerId(userId);
            review.setReviewerName(username != null ? username : "Unknown User");

        } else {
            throw new RuntimeException("Invalid authentication type: expected JWT from Keycloak");
        }
        reviewRepo.save(review);
        updateProductAverageRating(product);

        return review;
    }


    /**
     * Update an existing review and recalculate product rating.
     */
    @Transactional
    public Review updateReview(Long reviewId, ReviewDto dto) {
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setComment(dto.getComment());
        review.setRating(dto.getRating());

        // Get Keycloak user info from JWT token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getSubject();  // Keycloak user ID (sub claim)
            String username = jwtAuth.getToken().getClaimAsString("preferred_username");  // Keycloak username
            review.setReviewerId(userId);
            review.setReviewerName(username != null ? username : "Unknown User");
        } else {
            throw new RuntimeException("Invalid authentication type: expected JWT from Keycloak");
        }

        reviewRepo.save(review);
        updateProductAverageRating(review.getProduct());
        return review;
    }


    /**
     * Delete a review and recalculate product rating.
     */
    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        Product product = review.getProduct();
        reviewRepo.delete(review);
        updateProductAverageRating(product);
    }

    /**
     * Calculates and updates the product's average rating based on all its reviews.
     * Also updates the product's review field with all comments from all reviews.
     */
    private void updateProductAverageRating(Product product) {
        List<Review> reviews = reviewRepo.findByProduct(product);

        double avgRating = reviews.isEmpty()
                ? 0.0
                : reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        product.setRate(avgRating);
        
        // Update product review field with all comments from all reviews
        if (!reviews.isEmpty()) {
            // Sort reviews by creation date (most recent first) and collect all comments
            List<String> allComments = reviews.stream()
                    .sorted(Comparator.comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .filter(review -> review.getComment() != null && !review.getComment().trim().isEmpty())
                    .map(review -> {
                        String reviewer = review.getReviewerName() != null ? review.getReviewerName() : "Anonymous";
                        return String.format("[%s] %s", reviewer, review.getComment());
                    })
                    .collect(Collectors.toList());
            
            product.setReview(allComments);
        } else {
            product.setReview(new ArrayList<>());
        }
        
        productRepo.save(product);
    }
    @Transactional
    public List<Review> getReviewsByProduct(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return reviewRepo.findByProduct(product);
    }

}

