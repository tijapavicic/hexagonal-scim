import { describe, it, expect, vi, beforeEach } from 'vitest';

// Hoist mocks before module import
const { getJsonMock, postJsonMock } = vi.hoisted(() => ({
  getJsonMock: vi.fn(),
  postJsonMock: vi.fn(),
}));

vi.mock('./http', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock,
  ApiHttpError: class ApiHttpError extends Error {
    status: number;
    constructor(status: number, msg: string) {
      super(msg);
      this.status = status;
    }
  },
}));

import { createPayment, getPaymentById, listPayments } from './payments';
import type { PaymentDto } from '../types/payment.dto';

const paymentA: PaymentDto = {
  id: 1,
  productId: 1,
  quantity: 1,
  totalAmount: '100.00',
  currency: 'EUR',
  status: 'PENDING',
  paymentMethod: 'PAYPAL',
  userId: 42,
  accountId: 7,
};

const paymentB: PaymentDto = {
  id: 2,
  productId: 1,
  quantity: 2,
  totalAmount: '49.99',
  currency: 'USD',
  status: 'COMPLETED',
  paymentMethod: 'BANK_ACCOUNT',
  userId: null,
  accountId: null,
};

describe('api/payments', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('listPayments', () => {
    it('calls GET /api/v1/payments with page and size', async () => {
      getJsonMock.mockResolvedValue([paymentA, paymentB]);
      const result = await listPayments(0, 10);
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/payments?page=0&size=10');
      expect(result).toEqual([paymentA, paymentB]);
    });

    it('uses defaults page=0 size=10 when no params provided', async () => {
      getJsonMock.mockResolvedValue([]);
      await listPayments();
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/payments?page=0&size=10');
    });

    it('propagates ApiHttpError on failure', async () => {
      getJsonMock.mockRejectedValue(new Error('Request failed (403).'));
      await expect(listPayments()).rejects.toThrow('403');
    });
  });

  describe('getPaymentById', () => {
    it('calls GET /api/v1/payments/{id}', async () => {
      getJsonMock.mockResolvedValue(paymentA);
      const result = await getPaymentById(1);
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/payments/1');
      expect(result).toEqual(paymentA);
    });

    it('propagates error when payment not found', async () => {
      getJsonMock.mockRejectedValue(new Error('Request failed (404).'));
      await expect(getPaymentById(999)).rejects.toThrow('404');
    });
  });

  describe('createPayment', () => {
    it('calls POST /api/v1/payments with body', async () => {
      const body = { productId: 1, quantity: 1, paymentMethod: 'PAYPAL', currency: 'USD', accountId: 7 };
      postJsonMock.mockResolvedValue({ ...paymentA, ...body, id: 10 });
      const result = await createPayment(body);
      expect(postJsonMock).toHaveBeenCalledWith('/api/v1/payments', body);
      expect(result.id).toBe(10);
    });

    it('propagates 400 validation failure', async () => {
      postJsonMock.mockRejectedValue(new Error('quantity must be > 0'));
      await expect(createPayment({ productId: 1, quantity: 0, paymentMethod: 'PAYPAL', currency: 'EUR' }))
        .rejects.toThrow('quantity must be > 0');
    });
  });
});

