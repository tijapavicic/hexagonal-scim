/**
 * Transport-layer payment DTOs matching backend API payload shape.
 */
export interface PaymentDto {
  id: number;
  amount: string;
  currency: string;
  status: string;
  userId: number | null;
  createdAt: string;
}

/** Fields required to create a payment. */
export interface CreatePaymentDto {
  amount: string;
  currency: string;
  status: string;
  userId?: number;
}

