import { getJson, postJson } from './http';
import type { CreatePaymentDto, PaymentDto } from '../types/payment.dto';

const BASE = '/api/v1/payments';

/**
 * List payments using backend pagination query params.
 */
export async function listPayments(page = 0, size = 10): Promise<PaymentDto[]> {
  return getJson<PaymentDto[]>(`${BASE}?page=${page}&size=${size}`);
}

/** Fetch a single payment by numeric ID. */
export async function getPaymentById(id: number): Promise<PaymentDto> {
  return getJson<PaymentDto>(`${BASE}/${id}`);
}

/** Create a new payment. Returns the persisted entity (201 Created). */
export async function createPayment(body: CreatePaymentDto): Promise<PaymentDto> {
  return postJson<PaymentDto>(BASE, body);
}

