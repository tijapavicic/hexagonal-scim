package com.example.user.model;

/** Supported payment channels. Each value maps to a concrete {@code PaymentGatewayPort} strategy. */
public enum PaymentMethod {
    BANK_ACCOUNT,
    PAYPAL,
    IDEAL
}

