package com.example.user.api.payment;

import com.example.user.api.payment.dto.CreatePaymentRequest;
import com.example.user.api.payment.dto.PaymentResponse;
import com.example.user.model.Payment;
import com.example.user.port.in.GetAllPaymentsPort;
import com.example.user.port.in.GetPaymentPort;
import com.example.user.port.in.InitiatePaymentPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public PaymentControllerAdapter(
            InitiatePaymentPort initiatePaymentPort,
            GetPaymentPort getPaymentPort,
            GetAllPaymentsPort getAllPaymentsPort
    ) {
        this.initiatePaymentPort = initiatePaymentPort;
        this.getPaymentPort = getPaymentPort;
        this.getAllPaymentsPort = getAllPaymentsPort;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = initiatePaymentPort.initiate(
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

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.productId(),
                payment.quantity(),
                payment.totalAmount(),
                payment.currency(),
                payment.status(),
                payment.paymentMethod()
        );
    }
}

