package com.example.user.core;

import com.example.user.model.Account;
import com.example.user.model.BaseCatHouse;
import com.example.user.model.Payment;
import com.example.user.model.PaymentMethod;
import com.example.user.model.PaymentStatus;
import com.example.user.model.Product;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.out.DebitAccountPort;
import com.example.user.port.out.PaymentGatewayPort;
import com.example.user.port.out.PaymentRepositoryPort;
import com.example.user.port.out.PaymentStrategyPort;
import com.example.user.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentServiceTest {

    @Test
    void initiateUsesProductCurrencyByDefaultWhenRequestCurrencyMissing() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));
        RecordingDebitAccountPort debitPort = new RecordingDebitAccountPort();

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.COMPLETED),
                new FixedAccountPort(new BigDecimal("1000.00")),
                debitPort,
                new BigDecimal("1.10")
        );

        Payment payment = service.initiate(1L, 2, PaymentMethod.PAYPAL, null);

        assertEquals("EUR", payment.currency());
        assertEquals(new BigDecimal("159.98"), payment.totalAmount());
        assertEquals(8, products.findById(1L).orElseThrow().stockQuantity());
        assertEquals(0, debitPort.invocations());
    }

    @Test
    void initiateConvertsEurToUsdUsingInjectedRate() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.COMPLETED),
                new FixedAccountPort(new BigDecimal("1000.00")),
                new RecordingDebitAccountPort(),
                new BigDecimal("1.10")
        );

        Payment payment = service.initiate(1L, 1, PaymentMethod.IDEAL, "USD");

        assertEquals("USD", payment.currency());
        assertEquals(new BigDecimal("87.99"), payment.totalAmount());
    }

    @Test
    void initiateRejectsUnsupportedCurrency() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.COMPLETED),
                new FixedAccountPort(new BigDecimal("1000.00")),
                new RecordingDebitAccountPort(),
                new BigDecimal("1.10")
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.initiate(1L, 1, PaymentMethod.BANK_ACCOUNT, "GBP"));
    }

    @Test
    void initiateRejectsWhenAccountBalanceIsInsufficient() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));
        RecordingDebitAccountPort debitPort = new RecordingDebitAccountPort();

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.COMPLETED),
                new FixedAccountPort(new BigDecimal("50.00")),
                debitPort,
                new BigDecimal("1.10")
        );

        assertThrows(InsufficientFundsException.class,
                () -> service.initiate(42L, 7L, 1L, 1, PaymentMethod.PAYPAL, "EUR"));
        assertEquals(0, debitPort.invocations());
    }

    @Test
    void initiateDebitsAccountWhenPaymentCompletes() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));
        RecordingDebitAccountPort debitPort = new RecordingDebitAccountPort();

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.COMPLETED),
                new FixedAccountPort(new BigDecimal("500.00")),
                debitPort,
                new BigDecimal("1.10")
        );

        Payment payment = service.initiate(42L, 7L, 1L, 1, PaymentMethod.PAYPAL, "EUR");

        assertEquals(1, debitPort.invocations());
        assertEquals(7L, debitPort.lastAccountId());
        assertEquals(payment.totalAmount(), debitPort.lastAmount());
    }

    @Test
    void initiateDoesNotDebitAccountWhenPaymentFails() {
        InMemoryProductRepo products = new InMemoryProductRepo();
        products.save(new BaseCatHouse(1L, 10));
        RecordingDebitAccountPort debitPort = new RecordingDebitAccountPort();

        PaymentService service = new PaymentService(
                products,
                new InMemoryPaymentRepo(),
                new FixedStrategy(PaymentStatus.FAILED),
                new FixedAccountPort(new BigDecimal("500.00")),
                debitPort,
                new BigDecimal("1.10")
        );

        Payment payment = service.initiate(42L, 7L, 1L, 1, PaymentMethod.PAYPAL, "EUR");

        assertEquals(PaymentStatus.FAILED, payment.status());
        assertEquals(0, debitPort.invocations());
    }

    private static final class FixedStrategy implements PaymentStrategyPort {
        private final PaymentGatewayPort gateway;

        private FixedStrategy(PaymentStatus resultStatus) {
            this.gateway = new PaymentGatewayPort() {
                @Override
                public PaymentMethod paymentMethod() {
                    return PaymentMethod.PAYPAL;
                }

                @Override
                public PaymentStatus process(Payment payment) {
                    return resultStatus;
                }
            };
        }

        @Override
        public PaymentGatewayPort resolve(PaymentMethod paymentMethod) {
            return gateway;
        }
    }

    private static final class InMemoryProductRepo implements ProductRepositoryPort {
        private final Map<Long, Product> store = new HashMap<>();

        @Override
        public Product save(Product product) {
            store.put(product.id(), product);
            return product;
        }

        @Override
        public Product update(Product product) {
            store.put(product.id(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean existsByName(String name) {
            return store.values().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }
    }

    private static final class InMemoryPaymentRepo implements PaymentRepositoryPort {
        private final AtomicLong sequence = new AtomicLong(0);
        private final Map<Long, Payment> store = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            Long id = payment.id() != null ? payment.id() : sequence.incrementAndGet();
            Payment persisted = new Payment(
                    id,
                    payment.userId(),
                    payment.accountId(),
                    payment.productId(),
                    payment.quantity(),
                    payment.totalAmount(),
                    payment.currency(),
                    payment.status(),
                    payment.paymentMethod()
            );
            store.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<Payment> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Payment> findAll() {
            return new ArrayList<>(store.values());
        }
    }

    private static final class FixedAccountPort implements GetAccountPort {
        private final BigDecimal balance;

        private FixedAccountPort(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public Account getById(Long userId, Long accountId) {
            return new Account(accountId, userId, "Main", balance);
        }
    }

    private static final class RecordingDebitAccountPort implements DebitAccountPort {
        private int invocations;
        private Long lastAccountId;
        private BigDecimal lastAmount;

        @Override
        public void debit(Long accountId, BigDecimal amount) {
            invocations++;
            lastAccountId = accountId;
            lastAmount = amount;
        }

        int invocations() {
            return invocations;
        }

        Long lastAccountId() {
            return lastAccountId;
        }

        BigDecimal lastAmount() {
            return lastAmount;
        }
    }
}

