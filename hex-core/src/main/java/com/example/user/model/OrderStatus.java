package com.example.user.model;

/**
 * Order lifecycle states for marketplace orders.
 *
 * Lifecycle: PENDING_PAYMENT → PAID → SHIPPED → DELIVERED → COMPLETED
 * Alternative: CANCELLED can occur at any stage
 *
 * State machine enforced at domain model level.
 */
public enum OrderStatus {
    /** Order created, awaiting payment */
    PENDING_PAYMENT,

    /** Payment successfully received */
    PAID,

    /** Order shipped to buyer */
    SHIPPED,

    /** Order delivered to buyer */
    DELIVERED,

    /** Order completed (can request refund after this) */
    COMPLETED,

    /** Order cancelled (refund issued if applicable) */
    CANCELLED
}

