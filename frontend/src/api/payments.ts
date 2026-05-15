import { getJson, postJson } from './http';
import type { CreatePaymentDto, PagedPaymentResponse, PaymentDto } from '../types/payment.dto';
import { createLogger } from '../logger/logger';

const logger = createLogger('PaymentsAPI');
const BASE = '/api/v1/payments';

/**
 * List payments using backend pagination query params.
 * Returns the payment DTOs from the paginated response.
 */
export async function listPayments(page = 0, size = 10): Promise<PaymentDto[]> {
  try {
    logger.info('Fetching payments', { page, size });
    const response = await getJson<PagedPaymentResponse>(`${BASE}?page=${page}&size=${size}`);
    logger.info('Payments fetched successfully', {
      count: response.content.length,
      totalElements: response.totalElements,
      pageNumber: response.pageNumber,
      totalPages: response.totalPages,
    });
    return response.content;
  } catch (error) {
    logger.error('Failed to fetch payments', {
      page,
      size,
      error: error instanceof Error ? error.message : String(error),
    });
    throw error;
  }
}

/** Fetch a single payment by numeric ID. */
export async function getPaymentById(id: number): Promise<PaymentDto> {
  try {
    logger.info('Fetching payment by ID', { id });
    const payment = await getJson<PaymentDto>(`${BASE}/${id}`);
    logger.info('Payment fetched', { id, status: payment.status });
    return payment;
  } catch (error) {
    logger.error('Failed to fetch payment', {
      id,
      error: error instanceof Error ? error.message : String(error),
    });
    throw error;
  }
}

/** Create a new payment. Returns the persisted entity (201 Created). */
export async function createPayment(body: CreatePaymentDto): Promise<PaymentDto> {
  try {
    logger.info('Creating payment', {
      productId: body.productId,
      quantity: body.quantity,
      paymentMethod: body.paymentMethod,
    });
    const payment = await postJson<PaymentDto>(BASE, body);
    logger.info('Payment created', {
      id: payment.id,
      status: payment.status,
    });
    return payment;
  } catch (error) {
    logger.error('Failed to create payment', {
      error: error instanceof Error ? error.message : String(error),
    });
    throw error;
  }
}

