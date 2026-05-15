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
 * - `userId` is resolved automatically by the backend from the JWT email claim.
 * - `accountId` is optional. When provided, the server checks the balance and
 *   debits it on success.
 */
export interface CreatePaymentDto {
  productId: number;
  quantity: number;
  /** BANK_ACCOUNT | PAYPAL | IDEAL */
  paymentMethod: string;
  /** EUR | USD */
  currency: string;
  /** Optional: your Account ID. The server resolves your user automatically. */
  accountId?: number;
}
