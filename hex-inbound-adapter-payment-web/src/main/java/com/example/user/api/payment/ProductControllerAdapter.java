package com.example.user.api.payment;

import com.example.user.api.payment.dto.CreateProductRequest;
import com.example.user.api.payment.dto.ProductResponse;
import com.example.user.api.payment.dto.UpdateProductRequest;
import com.example.user.model.Product;
import com.example.user.port.in.CreateProductPort;
import com.example.user.port.in.DeleteProductPort;
import com.example.user.port.in.GetAllProductsPort;
import com.example.user.port.in.GetProductPort;
import com.example.user.port.in.UpdateProductPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class ProductControllerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ProductControllerAdapter.class);

    private final CreateProductPort createProductPort;
    private final GetProductPort getProductPort;
    private final GetAllProductsPort getAllProductsPort;
    private final UpdateProductPort updateProductPort;
    private final DeleteProductPort deleteProductPort;

    public ProductControllerAdapter(
            CreateProductPort createProductPort,
            GetProductPort getProductPort,
            GetAllProductsPort getAllProductsPort,
            UpdateProductPort updateProductPort,
            DeleteProductPort deleteProductPort
    ) {
        this.createProductPort = createProductPort;
        this.getProductPort = getProductPort;
        this.getAllProductsPort = getAllProductsPort;
        this.updateProductPort = updateProductPort;
        this.deleteProductPort = deleteProductPort;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        LOG.info("Creating product: name={}, price={} {}",
                request.name(), request.price(), request.currency());
        Product created = createProductPort.create(
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.stockQuantity()
        );
        LOG.info("Product created: id={}, name={}", created.id(), created.name());
        return toResponse(created);
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        LOG.info("Fetching all products");
        List<ProductResponse> products = getAllProductsPort.getAll().stream().map(this::toResponse).toList();
        LOG.info("Products fetched: count={}", products.size());
        return products;
    }

    @GetMapping("/{id}")
    public ProductResponse getById(
            @PathVariable("id") @Positive(message = "id must be a positive number") Long id) {
        LOG.info("Fetching product by id: {}", id);
        ProductResponse response = toResponse(getProductPort.getById(id));
        LOG.info("Product fetched: id={}, name={}", response.id(), response.name());
        return response;
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable("id") @Positive(message = "id must be a positive number") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        LOG.info("Updating product: id={}, name={}", id, request.name());
        Product updated = updateProductPort.update(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.stockQuantity()
        );
        LOG.info("Product updated: id={}", id);
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") @Positive(message = "id must be a positive number") Long id) {
        LOG.info("Deleting product: id={}", id);
        deleteProductPort.deleteById(id);
        LOG.info("Product deleted: id={}", id);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.description(),
                product.price(),
                product.currency(),
                product.stockQuantity()
        );
    }
}
