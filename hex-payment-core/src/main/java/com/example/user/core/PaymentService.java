package com.example.user.core;

import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;
import com.example.user.model.Product;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.out.PaymentGatewayPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;

import java.math.BigDecimal;
import java.util.List;

public class PaymentService implements InitiatePaymentPort, GetPaymentPort, GetAllPaymentsPort {

    private final ProductRepositoryPort productRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentStrategyPort paymentStrategyPort;

    public PaymentService(
            ProductRepositoryPort productRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentStrategyPort paymentStrategyPort
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.paymentStrategyPort = paymentStrategyPort;
    }

    @Override
    public Payment initiate(Long productId, int quantity, PaymentMethod paymentMethod) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for id: " + productId));

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        if (product.stockQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product id: " + productId + ". Requested: " + quantity +
                            ", available: " + product.stockQuantity());
        }

        BigDecimal totalAmount = product.price().multiply(BigDecimal.valueOf(quantity));

        Payment pending = paymentRepositoryPort.save(new Payment(
                null,
                productId,
                quantity,
                totalAmount,
                product.currency(),
                PaymentStatus.PENDING,
                paymentMethod
        ));

        PaymentGatewayPort gateway = paymentStrategyPort.resolve(paymentMethod);

        try {
            PaymentStatus finalStatus = gateway.process(pending);
            Payment finalized = paymentRepositoryPort.save(new Payment(
                    pending.id(),
                    pending.productId(),
                    pending.quantity(),
                    pending.totalAmount(),
                    pending.currency(),
                    finalStatus,
                    pending.paymentMethod()
            ));

            if (finalStatus == PaymentStatus.COMPLETED) {
                int newStock = product.stockQuantity() - quantity;
                productRepositoryPort.update(new Product(
                        product.id(),
                        product.name(),
                        product.description(),
                        product.price(),
                        product.currency(),
                        newStock
                ));
            }

            return finalized;
        } catch (RuntimeException ex) {
            paymentRepositoryPort.save(new Payment(
                    pending.id(),
                    pending.productId(),
                    pending.quantity(),
                    pending.totalAmount(),
                    pending.currency(),
                    PaymentStatus.FAILED,
                    pending.paymentMethod()
            ));
            throw new PaymentProcessingException(
                    "Payment processing failed for method: " + paymentMethod + ", payment id: " + pending.id(), ex);
        }
    }

    @Override
    public Payment getById(Long id) {
        return paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for id: " + id));
    }

    @Override
    public List<Payment> getAll() {
        return paymentRepositoryPort.findAll();
    }
}

