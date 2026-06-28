package com.example.user.api.payment;

import com.example.user.api.payment.config.ApiPaginationProperties;
import com.example.user.api.payment.dto.CreatePaymentRequest;
import com.example.user.api.payment.dto.PagedPaymentResponse;
import com.example.user.api.payment.dto.PaymentResponse;
import com.example.user.model.Payment;
import com.example.user.model.PagedPayments;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import com.example.user.port.in.ResolvePayerPort;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payments")
public class PaymentControllerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentControllerAdapter.class);

    private final InitiatePaymentPort initiatePaymentPort;
    private final GetPaymentPort getPaymentPort;
    private final GetAllPaymentsPort getAllPaymentsPort;
    private final ResolvePayerPort resolvePayerPort;
    private final ApiPaginationProperties apiPaginationProperties;

    public PaymentControllerAdapter(
            InitiatePaymentPort initiatePaymentPort,
            GetPaymentPort getPaymentPort,
            GetAllPaymentsPort getAllPaymentsPort,
            ResolvePayerPort resolvePayerPort,
            ApiPaginationProperties apiPaginationProperties
    ) {
        this.initiatePaymentPort = initiatePaymentPort;
        this.getPaymentPort = getPaymentPort;
        this.getAllPaymentsPort = getAllPaymentsPort;
        this.resolvePayerPort = resolvePayerPort;
        this.apiPaginationProperties = apiPaginationProperties;
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
        LOG.info("Creating payment: productId={}, quantity={}, paymentMethod={}, currency={}",
                request.productId(), request.quantity(), request.paymentMethod(), request.currency());

        Long resolvedUserId = resolveUserId(authentication);

        // If the caller wants to debit an account, the user must be resolvable.
        if (request.accountId() != null && resolvedUserId == null) {
            LOG.warn("Payment creation failed: user not resolvable from email claim, accountId={}",
                    request.accountId());
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
        LOG.info("Payment created successfully: id={}, status={}", payment.id(), payment.status());
        return toResponse(payment);
    }

    @GetMapping
    public PagedPaymentResponse getAll(
            @Parameter(description = "Zero-indexed page number (uses configured default when absent)")
            @RequestParam(name = "page", required = false) @Min(value = 0, message = "page must be >= 0") Integer page,

            @Parameter(description = "Items per page, max 100 (uses configured default when absent)")
            @RequestParam(name = "size", required = false) @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") Integer size,

            @Parameter(description = "Set to `false` to return ALL payments without paging")
            @RequestParam(name = "pageable", required = false) Boolean pageable
    ) {
        int effectivePage = page != null ? page : apiPaginationProperties.getDefaultPage();
        int effectiveSize = size != null ? size : apiPaginationProperties.getDefaultSize();
        boolean effectivePageable = pageable != null ? pageable : apiPaginationProperties.isDefaultPageable();

        LOG.info("Fetching payments: page={}, size={}, pageable={}",
                effectivePage, effectiveSize, effectivePageable);

        PagedPayments pagedPayments = getAllPaymentsPort.getAll(effectivePage, effectiveSize, effectivePageable);

        LOG.info("Payments fetched: totalElements={}, totalPages={}, currentPage={}, itemCount={}",
                pagedPayments.totalElements(), pagedPayments.totalPages(),
                pagedPayments.pageNumber(), pagedPayments.content().size());

        return new PagedPaymentResponse(
                pagedPayments.content().stream()
                        .map(this::toResponse)
                        .toList(),
                pagedPayments.pageNumber(),
                pagedPayments.pageSize(),
                pagedPayments.totalElements(),
                pagedPayments.totalPages(),
                pagedPayments.pageNumber() < pagedPayments.totalPages() - 1,
                pagedPayments.pageNumber() > 0
        );
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(
            @PathVariable("id") @Positive(message = "id must be a positive number") Long id) {
        LOG.info("Fetching payment by id: {}", id);
        PaymentResponse response = toResponse(getPaymentPort.getById(id));
        LOG.info("Payment fetched: id={}, status={}", response.id(), response.status());
        return response;
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


