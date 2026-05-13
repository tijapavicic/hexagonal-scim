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
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/products")
public class ProductControllerAdapter {

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
        Product created = createProductPort.create(
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.stockQuantity()
        );
        return toResponse(created);
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        return getAllProductsPort.getAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable("id") Long id) {
        return toResponse(getProductPort.getById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateProductRequest request) {
        Product updated = updateProductPort.update(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.stockQuantity()
        );
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        deleteProductPort.deleteById(id);
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

