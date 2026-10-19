package tn.iteam.catalogueservice.exceptions;

/**
 * Raised when a reservation asks for more units than the product has left.
 *
 * <p>Carries the numbers so the consumer can publish a {@code
 * stock.reservation-failed} event that tells the customer what is actually left,
 * instead of a bare "failed".
 */
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final Integer requested;
    private final Integer available;

    public InsufficientStockException(Long productId, Integer requested, Integer available) {
        super("Insufficient stock for product " + productId + ": requested " + requested
                + ", available " + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getRequested() {
        return requested;
    }

    public Integer getAvailable() {
        return available;
    }
}