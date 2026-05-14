package com.example.user.core;

import com.example.user.model.BaseCatHouse;
import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;
import com.example.user.model.Product;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.out.DebitAccountPort;
import com.example.user.port.out.PaymentGatewayPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PaymentService implements InitiatePaymentPort, GetPaymentPort, GetAllPaymentsPort {
    private static final String EUR = "EUR";
    private static final String USD = "USD";

    private final ProductRepositoryPort productRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentStrategyPort paymentStrategyPort;
    private final GetAccountPort getAccountPort;
    private final DebitAccountPort debitAccountPort;
    private final BigDecimal eurToUsdRate;

    public PaymentService(
            ProductRepositoryPort productRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentStrategyPort paymentStrategyPort,
            GetAccountPort getAccountPort,
            DebitAccountPort debitAccountPort,
            BigDecimal eurToUsdRate
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.paymentStrategyPort = paymentStrategyPort;
        this.getAccountPort = getAccountPort;
        this.debitAccountPort = debitAccountPort;
        this.eurToUsdRate = Objects.requireNonNull(eurToUsdRate, "eurToUsdRate must not be null");
        if (eurToUsdRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("eurToUsdRate must be > 0");
        }
    }

    @Override
    public Payment initiate(Long userId, Long accountId, Long productId, int quantity, PaymentMethod paymentMethod, String requestedCurrency) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for id: " + productId));

        if (!BaseCatHouse.isSameProduct(product)) {
            throw new UnsupportedProductException("Only BaseCatHouse can be sold in the current production phase.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        if (product.stockQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product id: " + productId + ". Requested: " + quantity +
                            ", available: " + product.stockQuantity());
        }

        String chargeCurrency = resolveChargeCurrency(requestedCurrency, product.currency());
        BigDecimal unitPrice = resolveUnitPrice(product, chargeCurrency);
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        if (userId != null || accountId != null) {
            if (userId == null || accountId == null) {
                throw new IllegalArgumentException("Both userId and accountId must be provided together");
            }
            BigDecimal balance = getAccountPort.getById(userId, accountId).balance();
            if (balance.compareTo(totalAmount) < 0) {
                throw new InsufficientFundsException("Insufficient funds for account id: " + accountId);
            }
        }

        Payment pending = paymentRepositoryPort.save(new Payment(
                null,
                userId,
                accountId,
                productId,
                quantity,
                totalAmount,
                chargeCurrency,
                PaymentStatus.PENDING,
                paymentMethod
        ));

        PaymentGatewayPort gateway = paymentStrategyPort.resolve(paymentMethod);

        try {
            PaymentStatus finalStatus = gateway.process(pending);
            Payment finalized = paymentRepositoryPort.save(new Payment(
                    pending.id(),
                    pending.userId(),
                    pending.accountId(),
                    pending.productId(),
                    pending.quantity(),
                    pending.totalAmount(),
                    chargeCurrency,
                    finalStatus,
                    pending.paymentMethod()
            ));

            if (finalStatus == PaymentStatus.COMPLETED) {
                if (pending.accountId() != null) {
                    debitAccountPort.debit(pending.accountId(), pending.totalAmount());
                }
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
                    pending.userId(),
                    pending.accountId(),
                    pending.productId(),
                    pending.quantity(),
                    pending.totalAmount(),
                    chargeCurrency,
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

    private String resolveChargeCurrency(String requestedCurrency, String productCurrency) {
        if (requestedCurrency == null || requestedCurrency.isBlank()) {
            return productCurrency;
        }
        String normalized = requestedCurrency.toUpperCase(Locale.ROOT);
        if (!EUR.equals(normalized) && !USD.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported currency: " + requestedCurrency + ". Allowed: EUR, USD");
        }
        return normalized;
    }

    private BigDecimal resolveUnitPrice(Product product, String chargeCurrency) {
        String baseCurrency = product.currency().toUpperCase(Locale.ROOT);
        if (chargeCurrency.equals(baseCurrency)) {
            return product.price().setScale(2, RoundingMode.HALF_UP);
        }
        if (EUR.equals(baseCurrency) && USD.equals(chargeCurrency)) {
            return product.price().multiply(eurToUsdRate).setScale(2, RoundingMode.HALF_UP);
        }
        throw new IllegalArgumentException(
                "Unsupported currency conversion from " + product.currency() + " to " + chargeCurrency);
    }
}

