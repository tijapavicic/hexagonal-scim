package com.example.user.config;

import com.example.user.core.ProductService;
import com.example.user.port.in.CreateProductPort;
import com.example.user.port.in.DeleteProductPort;
import com.example.user.port.in.GetAllProductsPort;
import com.example.user.port.in.GetProductPort;
import com.example.user.port.in.UpdateProductPort;
import com.example.user.port.out.ProductRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductConfig {

    @Bean
    ProductService productServiceBean(ProductRepositoryPort productRepositoryPort) {
        return new ProductService(productRepositoryPort);
    }

    @Bean
    CreateProductPort createProductPort(ProductService productServiceBean) {
        return productServiceBean::create;
    }

    @Bean
    GetProductPort getProductPort(ProductService productServiceBean) {
        return productServiceBean::getById;
    }

    @Bean
    GetAllProductsPort getAllProductsPort(ProductService productServiceBean) {
        return productServiceBean::getAll;
    }

    @Bean
    UpdateProductPort updateProductPort(ProductService productServiceBean) {
        return productServiceBean::update;
    }

    @Bean
    DeleteProductPort deleteProductPort(ProductService productServiceBean) {
        return productServiceBean::deleteById;
    }
}

