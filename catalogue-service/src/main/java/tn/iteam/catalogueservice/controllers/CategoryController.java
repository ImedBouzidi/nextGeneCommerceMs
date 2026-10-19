package tn.iteam.catalogueservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.iteam.catalogueservice.dto.CategoryDto;
import tn.iteam.catalogueservice.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "APIs for managing categories in the catalogue")
public class CategoryController {

    private final CategoryService categoryService;

    // ------------------------------------------------------------
    // 1. Get all categories
    // ------------------------------------------------------------
    @Operation(
            summary = "Retrieve all categories",
            description = "Returns the complete list of categories."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                    schema = @Schema(implementation = CategoryDto.class)
                            )))
    })
    @GetMapping("/all")
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getCategories());
    }

    // ------------------------------------------------------------
    // 2. Create a new category (admin only – optional)
    // ------------------------------------------------------------
    @Operation(
            summary = "Create a new category",
            description = "Adds a new category. Only users with **ADMIN** role can call this endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category created",
                    content = @Content(schema = @Schema(implementation = CategoryDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden – insufficient rights")
    })
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> createCategory(
            @Parameter(description = "Category data", required = true)
            @Valid @RequestBody CategoryDto dto) {  // ADD @Valid
        return ResponseEntity.ok(categoryService.addCategory(dto));
    }

    // ------------------------------------------------------------
    // 3. Update an existing category (admin only)
    // ------------------------------------------------------------
    @Operation(
            summary = "Update a category",
            description = "Updates the category identified by **id**. Only **ADMIN** users."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated",
                    content = @Content(schema = @Schema(implementation = CategoryDto.class))),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> updateCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated category data", required = true) @RequestBody CategoryDto dto) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    // ------------------------------------------------------------
    // 4. Delete a category (admin only)
    // ------------------------------------------------------------
    @Operation(
            summary = "Delete a category",
            description = "Removes the category with the given **id**. Only **ADMIN** users."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable Long id) {
        categoryService.removeCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // 5. Get a single category by ID
    // ------------------------------------------------------------
    @Operation(
            summary = "Get category by ID",
            description = "Returns a single category."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category found",
                    content = @Content(schema = @Schema(implementation = CategoryDto.class))),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategoryById(
            @Parameter(description = "Category ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategory(id));
    }

    // ------------------------------------------------------------
    // 6. Debug – current authenticated user (optional)
    // ------------------------------------------------------------
    @Operation(
            summary = "Get current authenticated user",
            description = "Utility endpoint that returns the username of the logged-in user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Username returned",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string")))
    })
    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUser() {
        return ResponseEntity.ok(categoryService.getCurrentConnectedUser());
    }
}