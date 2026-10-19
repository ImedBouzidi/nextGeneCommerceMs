package tn.iteam.catalogueservice.controllers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.iteam.catalogueservice.dto.ReviewDto;
import tn.iteam.catalogueservice.models.Review;
import tn.iteam.catalogueservice.service.ReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews", description = "Operations related to product reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ---------------- Add Review ----------------
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/product/{productId}")
    @Operation(summary = "Add a review to a product", description = "Adds a new review and updates the product's average rating")
    public ResponseEntity<Review> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewDto reviewDto
    ) {
        Review review = reviewService.addReview(productId, reviewDto);
        return ResponseEntity.ok(review);
    }


    // ---------------- Update Review ----------------
    @PutMapping("/{reviewId}")
    @Operation(summary = "Update an existing review", description = "Updates a review and recalculates product rating")
    public ResponseEntity<Review> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewDto reviewDto
    ) {
        Review updatedReview = reviewService.updateReview(reviewId, reviewDto);
        return ResponseEntity.ok(updatedReview);
    }

    // ---------------- Delete Review ----------------
    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete a review", description = "Deletes a review and updates the product's rating")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Get Reviews for a Product ----------------
    @GetMapping("/product/{productId}")
    @Operation(summary = "Get all reviews for a product", description = "Retrieves a list of reviews for the specified product")
    public ResponseEntity<List<Review>> getReviewsByProduct(@PathVariable Long productId) {
        List<Review> reviews = reviewService.getReviewsByProduct(productId);
        return ResponseEntity.ok(reviews);
    }
}

