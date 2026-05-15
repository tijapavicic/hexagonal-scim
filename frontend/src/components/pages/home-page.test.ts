import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// ── Mock getJson ─────────────────────────────────────────────────────────────
// vi.hoisted ensures the variable is available when the mock factory runs.
const { getJsonMock } = vi.hoisted(() => ({ getJsonMock: vi.fn() }));

vi.mock('../../api/http', () => ({
  getJson: getJsonMock,
  ApiHttpError: class ApiHttpError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

// ── Import component after mock ───────────────────────────────────────────────
import './home-page';

// ── Helpers ───────────────────────────────────────────────────────────────────
async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await new Promise((r) => setTimeout(r, 0));
}

function mount(username = 'testuser'): HTMLElement {
  const el = document.createElement('home-page');
  el.setAttribute('username', username);
  document.body.appendChild(el);
  return el;
}

function shadow(el: HTMLElement): ShadowRoot {
  return el.shadowRoot!;
}

// ── Tests ─────────────────────────────────────────────────────────────────────
describe('home-page', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.clearAllMocks();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('renders username in welcome heading', () => {
    getJsonMock.mockReturnValue(new Promise(() => {})); // deliberately pending
    const el = mount('alice');
    expect(shadow(el).textContent).toContain('alice');
  });

  it('shows loading skeletons before API calls resolve', () => {
    getJsonMock.mockReturnValue(new Promise(() => {})); // pending
    const el = mount();
    const skeletons = shadow(el).querySelectorAll('.skeleton');
    expect(skeletons.length).toBe(2); // one per live card
  });

  it('renders user count after users API resolves', async () => {
    getJsonMock
      .mockResolvedValueOnce([{ id: 1 }, { id: 2 }, { id: 3 }]) // users
      .mockResolvedValueOnce([]);                                  // payments

    const el = mount();
    await flushPromises();

    const cardValues = Array.from(shadow(el).querySelectorAll('.card-value'));
    const usersCard = cardValues.find((v) => v.closest('a[href="#/users"]'));
    expect(usersCard?.textContent?.trim()).toBe('3');
  });

  it('renders payment count after payments API resolves', async () => {
    getJsonMock
      .mockResolvedValueOnce([])                                    // users
      .mockResolvedValueOnce([{ id: 10 }, { id: 11 }]);            // payments

    const el = mount();
    await flushPromises();

    const cardValues = Array.from(shadow(el).querySelectorAll('.card-value'));
    const paymentsCard = cardValues.find((v) => v.closest('a[href="#/payments"]'));
    expect(paymentsCard?.textContent?.trim()).toBe('2');
  });

  it('shows error chip when users API fails', async () => {
    getJsonMock
      .mockRejectedValueOnce(new Error('network error')) // users fails
      .mockResolvedValueOnce([]);                         // payments ok

    const el = mount();
    await flushPromises();

    const chips = shadow(el).querySelectorAll('.error-chip');
    expect(chips.length).toBeGreaterThanOrEqual(1);
  });

  it('shows error chip when payments API fails', async () => {
    getJsonMock
      .mockResolvedValueOnce([])                          // users ok
      .mockRejectedValueOnce(new Error('network error')); // payments fails

    const el = mount();
    await flushPromises();

    const chips = shadow(el).querySelectorAll('.error-chip');
    expect(chips.length).toBeGreaterThanOrEqual(1);
  });

  it('renders both cards as links to correct routes', async () => {
    getJsonMock.mockResolvedValue([]);
    const el = mount();
    await flushPromises();

    expect(shadow(el).querySelector('a[href="#/users"]')).not.toBeNull();
    expect(shadow(el).querySelector('a[href="#/payments"]')).not.toBeNull();
  });

  it('renders "View all →" link text on summary cards', async () => {
    getJsonMock.mockResolvedValue([]);
    const el = mount();
    await flushPromises();

    const links = shadow(el).querySelectorAll('.card-link');
    expect(links.length).toBe(2);
    links.forEach((l) => expect(l.textContent).toContain('View all'));
  });
});

