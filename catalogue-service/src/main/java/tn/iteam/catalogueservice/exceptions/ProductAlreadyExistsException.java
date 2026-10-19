package tn.iteam.catalogueservice.exceptions;

public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException(String name) {
        super("Product already exists with name: " + name);
    }
}
