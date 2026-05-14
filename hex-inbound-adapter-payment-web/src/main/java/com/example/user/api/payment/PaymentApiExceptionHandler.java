package com.example.user.api.payment;

import com.example.user.api.payment.dto.ErrorResponse;
import com.example.user.core.DuplicateProductException;
import com.example.user.core.InsufficientFundsException;
import com.example.user.core.InsufficientStockException;
import com.example.user.core.PaymentNotFoundException;
import com.example.user.core.PaymentProcessingException;
import com.example.user.core.ProductCatalogModificationNotAllowedException;
import com.example.user.core.ProductNotFoundException;
import com.example.user.core.UnsupportedProductException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {ProductControllerAdapter.class, PaymentControllerAdapter.class})
@Order(0)
public class PaymentApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DuplicateProductException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateProduct(DuplicateProductException ex, HttpServletRequest req) {
        return new ErrorResponse("PRODUCT_ALREADY_EXISTS", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        return new ErrorResponse("INSUFFICIENT_STOCK", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest req) {
        return new ErrorResponse("INSUFFICIENT_FUNDS", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest req) {
        return new ErrorResponse("PAYMENT_NOT_FOUND", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(PaymentProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handlePaymentProcessing(PaymentProcessingException ex, HttpServletRequest req) {
        return new ErrorResponse("PAYMENT_PROCESSING_FAILED", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ProductCatalogModificationNotAllowedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleCatalogReadOnly(ProductCatalogModificationNotAllowedException ex, HttpServletRequest req) {
        return new ErrorResponse("PRODUCT_CATALOG_READ_ONLY", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(UnsupportedProductException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleUnsupportedProduct(UnsupportedProductException ex, HttpServletRequest req) {
        return new ErrorResponse("UNSUPPORTED_PRODUCT", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed");
        return new ErrorResponse("VALIDATION_ERROR", detail, req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return new ErrorResponse("INVALID_REQUEST", ex.getMessage(), req.getRequestURI());
    }
}

