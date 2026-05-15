package com.example.user.model;

/** Supported payment channels. Each value maps to a concrete {@code PaymentGatewayPort} strategy. */
public enum PaymentMethod {
    BANK_ACCOUNT,  // ACH, SEPA, wire transfers
    PAYPAL,        // PayPal digital wallet
    IDEAL,         // iDEAL (Netherlands/Europe)
    CREDIT_CARD    // Credit/debit card payments
}

