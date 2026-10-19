package tn.iteam.catalogueservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.EventPublisher;
import tn.iteam.common.kafka.Topics;
import tn.iteam.catalogueservice.dto.ProductDto;
import tn.iteam.catalogueservice.exceptions.InsufficientStockException;
import tn.iteam.catalogueservice.exceptions.ProductAlreadyExistsException;
import tn.iteam.catalogueservice.exceptions.ProductNotFoundException;
import tn.iteam.catalogueservice.interfaces.FileStorageService;
import tn.iteam.catalogueservice.interfaces.IProduct;
import tn.iteam.catalogueservice.mappers.ProductMapper;
import tn.iteam.catalogueservice.models.Category;
import tn.iteam.catalogueservice.models.ProcessedEvent;
import tn.iteam.catalogueservice.models.Product;
import tn.iteam.catalogueservice.repos.CategoryRepo;
import tn.iteam.catalogueservice.repos.ProcessedEventRepository;
import tn.iteam.catalogueservice.repos.ProductRepo;

import java.util.List;

@Service
@Slf4j
public class ProductService implements IProduct {

    /** Recorded on each consumed event so a redelivery cannot decrement twice. */
    private static final String CONSUMER_STOCK = "catalogue-service-stock";

    private final ProductMapper productMapper;
    private final EventPublisher eventPublisher;
    private final ProductRepo productRepo;
    private final CategoryRepo categoryRepo;
    private final FileStorageService fileStorageService;
    private final ProcessedEventRepository processedEventRepository;

    public ProductService(ProductMapper productMapper,
                          EventPublisher eventPublisher,
                          ProductRepo productRepo,
                          CategoryRepo categoryRepo,
                          FileStorageService fileStorageService,
                          ProcessedEventRepository processedEventRepository) {
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.fileStorageService = fileStorageService;
        this.processedEventRepository = processedEventRepository;
    }

    // ---------- Helper methods ----------
    private Product getProductOrThrow(Long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepo.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with ID: " + categoryId));
    }

    private void sendKafkaEvent(String message) {
        eventPublisher.publish(Topics.PRODUCT_EVENTS, message);
    }

    // ---------- CRUD METHODS ----------
    @Override
    public ProductDto addProduct(ProductDto productDto) {
        if (productRepo.existsByName(productDto.getName())) {
            throw new ProductAlreadyExistsException(productDto.getName());
        }

        Product product = productMapper.toEntity(productDto);
        product.setCategory(getCategoryOrThrow(productDto.getCategoryId()));
        product.setImages(productDto.getImages());

        Product savedProduct = productRepo.save(product);
        sendKafkaEvent("New product created: " + savedProduct.getName());

        return productMapper.toDto(savedProduct);
    }

    @Override
    public void removeProduct(Long id) {
        Product product = getProductOrThrow(id);
        productRepo.delete(product);
        sendKafkaEvent("Product deleted: " + product.getName());
    }

    @Override
    public ProductDto updateProduct(Long id, ProductDto productDto) {
        Product existingProduct = getProductOrThrow(id);

        // Prevent duplicate product names
        if (!existingProduct.getName().equalsIgnoreCase(productDto.getName())
                && productRepo.existsByName(productDto.getName())) {
            throw new ProductAlreadyExistsException(productDto.getName());
        }

        existingProduct.setName(productDto.getName());
        existingProduct.setDescription(productDto.getDescription());
        existingProduct.setPrice(productDto.getPrice());

        // Same reasoning as images: only overwrite stock when the client sent a value,
        // so an edit that omits quantity leaves the existing stock untouched.
        if (productDto.getQuantity() != null) {
            existingProduct.setQuantity(productDto.getQuantity());
        }

        // Only replace images when the client actually sent a list.
        // The admin edit form intentionally omits `images`, so assigning the DTO's
        // null straight through erased every product's images on any unrelated edit
        // (renaming, price change, ...) and left the files orphaned on disk.
        if (productDto.getImages() != null) {
            existingProduct.setImages(productDto.getImages());
        }

        if (productDto.getCategoryId() != null) {
            existingProduct.setCategory(getCategoryOrThrow(productDto.getCategoryId()));
        }

        Product updatedProduct = productRepo.save(existingProduct);
        sendKafkaEvent("Product updated: " + updatedProduct.getName());

        return productMapper.toDto(updatedProduct);
    }

    @Override
    public List<ProductDto> getProducts() {
        return productRepo.findAll().stream()
                .map(productMapper::toDto)
                .toList();
    }

    @Override
    public ProductDto getProduct(Long id) {
        return productMapper.toDto(getProductOrThrow(id));
    }

    /**
     * Reserves stock for a paid order.
     *
     * <p>Called only from the {@code stock.reserve} consumer, which makes
     * catalogue-service the single writer of {@code quantity}. Transactional plus a
     * pessimistic row lock means two reservations racing for the last unit cannot both
     * succeed — the old read-modify-write let both read quantity=1 and both write 0.
     *
     * @param eventId Kafka event id, recorded so a redelivery is a no-op.
     * @return remaining quantity after the reservation, or {@code null} when this was
     * a duplicate delivery that must not be applied again.
     * @throws ProductNotFoundException      if the product does not exist.
     * @throws InsufficientStockException    if there are not enough units left.
     */
    @Transactional
    public Integer reserveStock(Long productId, Integer quantity, String eventId) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid reservation quantity: " + quantity);
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("stock.reserve {} already applied — skipping duplicate delivery", eventId);
            return null;
        }

        Product product = productRepo.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Integer current = product.getQuantity();
        if (current == null || current < quantity) {
            throw new InsufficientStockException(productId, quantity, current == null ? 0 : current);
        }

        product.setQuantity(current - quantity);
        productRepo.save(product);
        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_STOCK));

        log.info("Reserved {} unit(s) of product {} (event {}), remaining {}",
                quantity, productId, eventId, product.getQuantity());
        return product.getQuantity();
    }
}
