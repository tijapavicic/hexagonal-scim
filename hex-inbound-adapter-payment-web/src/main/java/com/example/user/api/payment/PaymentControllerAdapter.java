package com.example.user.api.payment;

import com.example.user.api.payment.dto.CreatePaymentRequest;
import com.example.user.api.payment.dto.PaymentResponse;
import com.example.user.model.Payment;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.in.ResolvePayerPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentControllerAdapter {

    private final InitiatePaymentPort initiatePaymentPort;
    private final GetPaymentPort getPaymentPort;
    private final GetAllPaymentsPort getAllPaymentsPort;
    private final ResolvePayerPort resolvePayerPort;

    public PaymentControllerAdapter(
            InitiatePaymentPort initiatePaymentPort,
            GetPaymentPort getPaymentPort,
            GetAllPaymentsPort getAllPaymentsPort,
            ResolvePayerPort resolvePayerPort
    ) {
        this.initiatePaymentPort = initiatePaymentPort;
        this.getPaymentPort = getPaymentPort;
        this.getAllPaymentsPort = getAllPaymentsPort;
        this.resolvePayerPort = resolvePayerPort;
    }

    /**
     * Initiates a new payment.
     *
     * <p>The payer {@code userId} is resolved from the authenticated JWT {@code email}
     * claim only. If {@code accountId} is provided but the email cannot be mapped to a
     * system user, the request is rejected with 400.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  Authentication authentication) {
        Long resolvedUserId = resolveUserId(authentication);

        // If the caller wants to debit an account, the user must be resolvable.
        if (request.accountId() != null && resolvedUserId == null) {
            throw new IllegalArgumentException(
                    "Your user account could not be identified from the session. " +
                    "Ensure your profile email is registered as a system user.");
        }

        Payment payment = initiatePaymentPort.initiate(
                resolvedUserId,
                request.accountId(),
                request.productId(),
                request.quantity(),
                request.paymentMethod(),
                request.currency()
        );
        return toResponse(payment);
    }

    @GetMapping
    public List<PaymentResponse> getAll() {
        return getAllPaymentsPort.getAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable("id") Long id) {
        return toResponse(getPaymentPort.getById(id));
    }

    /**
     * Extracts the system user ID from the JWT {@code email} claim.
     * Returns {@code null} if the principal is not a JWT or the email is unknown.
     */
    private Long resolveUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return resolvePayerPort.findUserIdByEmail(email).orElse(null);
            }
        }
        return null;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.userId(),
                payment.accountId(),
                payment.productId(),
                payment.quantity(),
                payment.totalAmount(),
                payment.currency(),
                payment.status(),
                payment.paymentMethod()
        );
    }
}
