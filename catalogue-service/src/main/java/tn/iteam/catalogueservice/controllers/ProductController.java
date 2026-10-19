package tn.iteam.catalogueservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.iteam.catalogueservice.dto.ProductDto;
import tn.iteam.catalogueservice.exceptions.ProductAlreadyExistsException;
import tn.iteam.catalogueservice.exceptions.ProductNotFoundException;
import tn.iteam.catalogueservice.service.FileStorageServiceImpl;
import tn.iteam.catalogueservice.service.ProductService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Management", description = "APIs for managing products in the catalogue")
public class ProductController {

    private final ProductService productService;
    private final FileStorageServiceImpl fileStorageService;

    public ProductController(ProductService productService, FileStorageServiceImpl fileStorageService) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
    }

    // ➕ Create a new product
    @Operation(
            summary = "Create a new product",
            description = "Creates a new product with name, description, price, category, and multiple images.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product details and image files",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully",
                    content = @Content(schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or file upload error"),
            @ApiResponse(responseCode = "409", description = "Product with this name already exists")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(
            @Parameter(description = "Name of the product", required = true)
            @RequestParam("name") String name,
            @Parameter(description = "Description of the product", required = true)
            @RequestParam("description") String description,
            @Parameter(description = "Price of the product (must be positive)", required = true)
            @RequestParam("price") Double price,
            @Parameter(description = "Category ID the product belongs to", required = true)
            @RequestParam("categoryId") Long categoryId,
            @Parameter(description = "Units in stock. Omitted means none.")
            @RequestParam(value = "quantity", required = false) Integer quantity,
            @Parameter(description = "List of product images (JPEG/PNG)", required = true)
            @RequestParam("images") List<MultipartFile> images
    ) {
        try {
            ProductDto productDto = new ProductDto();
            productDto.setName(name);
            productDto.setDescription(description);
            productDto.setPrice(price);
            productDto.setCategoryId(categoryId);
            // Stock was silently dropped before: the client always sent this field but
            // the endpoint never read it, so every product was persisted with a NULL
            // quantity — which the storefront then rendered as "out of stock".
            productDto.setQuantity(quantity);
            List<String> storedFilenames = new ArrayList<>();
            for (MultipartFile file : images) {
                String filename = fileStorageService.storeFile(file, "products");
                storedFilenames.add(filename);
            }
            productDto.setImages(storedFilenames);
            ProductDto createdProduct = productService.addProduct(productDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
        } catch (ProductAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Product with name '" + e.getMessage() + "' already exists.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error creating product: " + e.getMessage());
        }
    }

    // 🧾 Get all products
    @Operation(summary = "Retrieve all products", description = "Returns a list of all products in the catalogue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of products retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductDto.class))))
    })
    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<ProductDto> products = productService.getProducts();
        return ResponseEntity.ok(products);
    }

    // 🔍 Get product by ID
    @Operation(summary = "Get product by ID", description = "Retrieves a single product by its unique ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found",
                    content = @Content(schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(
            @Parameter(description = "Unique ID of the product", required = true)
            @PathVariable Long id) {
        try {
            ProductDto product = productService.getProduct(id);
            return ResponseEntity.ok(product);
        } catch (ProductNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product with ID " + id + " not found.");
        }
    }

    // ✏️ Update product
    @Operation(summary = "Update an existing product", description = "Updates product details by ID. Only provided fields are updated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully",
                    content = @Content(schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "Product name already exists")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProduct(
            @Parameter(description = "ID of the product to update", required = true)
            @PathVariable Long id,

            @Valid @RequestBody ProductDto productDto) {
        try {
            ProductDto updatedProduct = productService.updateProduct(id, productDto);
            return ResponseEntity.ok(updatedProduct);
        } catch (ProductNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product with ID " + id + " not found.");
        } catch (ProductAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Product with name '" + e.getMessage() + "' already exists.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error updating product: " + e.getMessage());
        }
    }

        // ➖ Decrease product quantity
        //
        // The PUT /{id}/decrease endpoint was removed on purpose. Stock is now
        // mutated only by the stock.reserve Kafka consumer, so there is exactly one
        // writer of `quantity`. While this endpoint existed, order-service and
        // payment-service both called it during a single checkout, and each paid
        // order decremented twice.


    // ❌ Delete product
    @Operation(summary = "Delete a product", description = "Removes a product from the catalogue by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(
            @Parameter(description = "ID of the product to delete", required = true)
            @PathVariable Long id) {
        try {
            productService.removeProduct(id);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (ProductNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product with ID " + id + " not found.");
        }
    }


}