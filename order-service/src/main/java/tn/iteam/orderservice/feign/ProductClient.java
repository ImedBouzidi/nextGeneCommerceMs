package tn.iteam.orderservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.iteam.orderservice.config.FeignConfig;
import tn.iteam.orderservice.dto.ProductDto;

/**
 * Read-only view of the catalogue, used to validate an order at creation time.
 *
 * <p>The {@code decreaseQuantity} operation that used to live here is gone on purpose:
 * mutating stock belongs to catalogue-service alone and is triggered by a
 * {@code payment.paid} event. Having both services call it was the source of the
 * double decrement on every paid order.
 */
@FeignClient(name = "catalogue-service", configuration = FeignConfig.class)
public interface ProductClient {
    @GetMapping("/api/products/{id}")
    ProductDto getProductById(@PathVariable("id") Long id);
}