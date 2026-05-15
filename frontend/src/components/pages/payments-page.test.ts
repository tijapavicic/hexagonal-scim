import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { listPaymentsMock, getPaymentByIdMock, createPaymentMock, authProfileRef } = vi.hoisted(() => ({
  listPaymentsMock: vi.fn(),
  getPaymentByIdMock: vi.fn(),
  createPaymentMock: vi.fn(),
  authProfileRef: {
    preferredUsername: 'testuser',
    realmRoles: ['ROLE_USER'] as string[],
    tokenExpiresAt: null as string | null,
  },
}));

vi.mock('../../api/payments', () => ({
  listPayments: listPaymentsMock,
  getPaymentById: getPaymentByIdMock,
  createPayment: createPaymentMock,
}));

vi.mock('../../auth/keycloak', () => ({
  getAuthProfile: () => authProfileRef,
}));

vi.mock('../shared/notification-bar', () => ({}));
vi.mock('../shared/pagination-bar', () => ({}));
vi.mock('../shared/modal-dialog', () => ({}));

import './payments-page';

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await new Promise((r) => setTimeout(r, 0));
}

const paymentA = {
  id: 1,
  amount: '100.00',
  currency: 'EUR',
  status: 'PENDING',
  userId: 42,
  createdAt: '2026-05-15T12:00:00Z',
};

const paymentB = {
  id: 2,
  amount: '49.99',
  currency: 'USD',
  status: 'COMPLETED',
  userId: null,
  createdAt: '2026-05-15T13:00:00Z',
};

describe('payments-page component', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.clearAllMocks();
    // Default: ROLE_USER (read-only access)
    authProfileRef.realmRoles = ['ROLE_USER'];
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  function mount(): HTMLElement {
    const el = document.createElement('payments-page');
    document.body.appendChild(el);
    return el;
  }

  it('shows loading state while payments are being fetched', () => {
    listPaymentsMock.mockReturnValue(new Promise(() => {}));
    const el = mount();
    expect(el.textContent).toContain('Loading payments');
  });

  it('renders payment rows after successful load', async () => {
    listPaymentsMock.mockResolvedValue([paymentA, paymentB]);
    const el = mount();
    await flushPromises();

    expect(el.textContent).toContain('100.00');
    expect(el.textContent).toContain('USD');
    expect(el.textContent).toContain('COMPLETED');
  });

  it('shows empty state when list is empty', async () => {
    listPaymentsMock.mockResolvedValue([]);
    const el = mount();
    await flushPromises();
    expect(el.textContent).toContain('No payments found');
  });

  it('shows error state and retry button when API fails', async () => {
    listPaymentsMock.mockRejectedValue(new Error('payment network error'));
    const el = mount();
    await flushPromises();

    expect(el.textContent).toContain('payment network error');
    expect(el.querySelector('[data-action="reload"]')).not.toBeNull();
  });

  it('expands detail row on view action click', async () => {
    listPaymentsMock.mockResolvedValue([paymentA]);
    getPaymentByIdMock.mockResolvedValue(paymentA);
    const el = mount();
    await flushPromises();

    const view = el.querySelector<HTMLButtonElement>('[data-action="toggle-detail"]');
    expect(view).not.toBeNull();
    view!.click();
    await flushPromises();

    expect(el.querySelector('.detail-grid')).not.toBeNull();
    expect(el.textContent).toContain('Created At');
  });

  it('opens create modal and submits valid create payload', async () => {
    authProfileRef.realmRoles = ['ROLE_ADMIN'];
    listPaymentsMock.mockResolvedValue([paymentA]);
    createPaymentMock.mockResolvedValue({ ...paymentA, id: 99 });
    const el = mount();
    await flushPromises();

    const createBtn = el.querySelector<HTMLButtonElement>('[data-action="create"]');
    expect(createBtn).not.toBeNull();
    createBtn!.click();
    await flushPromises();

    const amountInput = el.querySelector<HTMLInputElement>('#p-amount');
    const currencySelect = el.querySelector<HTMLSelectElement>('#p-currency');
    const statusSelect = el.querySelector<HTMLSelectElement>('#p-status');
    const userIdInput = el.querySelector<HTMLInputElement>('#p-userId');
    expect(amountInput).not.toBeNull();
    amountInput!.value = '19.99';
    currencySelect!.value = 'GBP';
    statusSelect!.value = 'PENDING';
    userIdInput!.value = '7';

    el.querySelector('#payment-modal')?.dispatchEvent(new CustomEvent('dialog-confirm'));
    await flushPromises();

    expect(createPaymentMock).toHaveBeenCalledWith({
      amount: '19.99',
      currency: 'GBP',
      status: 'PENDING',
      userId: 7,
    });
  });

  it('hides create button for ROLE_USER', async () => {
    // authProfileRef.realmRoles is already ['ROLE_USER'] from beforeEach
    listPaymentsMock.mockResolvedValue([paymentA]);
    const el = mount();
    await flushPromises();
    expect(el.querySelector('[data-action="create"]')).toBeNull();
  });

  it('renders pagination-bar after successful list load', async () => {
    listPaymentsMock.mockResolvedValue([paymentA]);
    const el = mount();
    await flushPromises();
    expect(el.querySelector('pagination-bar')).not.toBeNull();
  });
});

