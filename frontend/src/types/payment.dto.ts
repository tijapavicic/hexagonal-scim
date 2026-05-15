/**
 * Transport-layer payment DTOs matching backend API payload shape.
 */
export interface PaymentDto {
  id: number;
  userId: number | null;
  accountId: number | null;
  productId: number | null;
  quantity: number;
  /** Calculated server-side: unit price × quantity. */
  totalAmount: string;
  currency: string;
  status: string;
  paymentMethod: string;
}

/**
 * Body for POST /api/v1/payments (self-service product purchase).
 *
 * - `productId`, `quantity`, `paymentMethod`, `currency` are required.
 * - `userId` + `accountId` are optional but **must be provided together** when
 *   account-debit is desired. Omit both for a no-debit purchase.
 */
export interface CreatePaymentDto {
  productId: number;
  quantity: number;
  /** BANK_ACCOUNT | PAYPAL | IDEAL */
  paymentMethod: string;
  /** EUR | USD */
  currency: string;
  /** Optional: your system User ID (visible in Users page). Must be paired with accountId. */
  userId?: number;
  /** Optional: your Account ID. Must be paired with userId. */
  accountId?: number;
}
