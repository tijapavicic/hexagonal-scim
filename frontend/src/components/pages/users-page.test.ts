import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// ── Hoist mocks ───────────────────────────────────────────────────────────────
const { listUsersMock, getUserByIdMock, createUserMock, updateUserMock, deleteUserMock, authProfileRef } =
  vi.hoisted(() => ({
    listUsersMock:    vi.fn(),
    getUserByIdMock:  vi.fn(),
    createUserMock:   vi.fn(),
    updateUserMock:   vi.fn(),
    deleteUserMock:   vi.fn(),
    authProfileRef: {
      preferredUsername: 'testuser',
      realmRoles: ['ROLE_USER'] as string[],
      tokenExpiresAt: null as string | null,
    },
  }));

vi.mock('../../api/users', () => ({
  listUsers:    listUsersMock,
  getUserById:  getUserByIdMock,
  createUser:   createUserMock,
  updateUser:   updateUserMock,
  deleteUser:   deleteUserMock,
}));

vi.mock('../../auth/keycloak', () => ({
  getAuthProfile: () => authProfileRef,
}));

// Stub shared Web Components so jsdom doesn't fail on unknown elements
vi.mock('../shared/notification-bar', () => ({}));
vi.mock('../shared/pagination-bar',   () => ({}));
vi.mock('../shared/modal-dialog',     () => ({}));

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await new Promise(r => setTimeout(r, 0));
}

import './users-page';

const ALICE = { id: 1, username: 'alice', email: 'alice@example.com', firstName: 'Alice', lastName: 'Smith', active: true };
const BOB   = { id: 2, username: 'bob',   email: 'bob@example.com',   firstName: 'Bob',   lastName: 'Jones', active: false };

describe('users-page component', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.clearAllMocks();
    authProfileRef.preferredUsername = 'testuser';
    authProfileRef.realmRoles = ['ROLE_USER'];
    authProfileRef.tokenExpiresAt = null;
  });

  afterEach(() => { document.body.innerHTML = ''; });

  function mount(): HTMLElement {
    const el = document.createElement('users-page');
    document.body.appendChild(el);
    return el;
  }

  // ── Loading state ─────────────────────────────────────────────────────────

  it('shows loading message while users are being fetched', () => {
    listUsersMock.mockReturnValue(new Promise(() => {})); // pending forever
    const el = mount();
    expect(el.textContent).toContain('Loading users');
  });

  // ── Successful list render ────────────────────────────────────────────────

  it('renders a table row for each user after load', async () => {
    listUsersMock.mockResolvedValue([ALICE, BOB]);
    const el = mount();
    await flushPromises();
    expect(el.textContent).toContain('alice');
    expect(el.textContent).toContain('bob');
    expect(el.textContent).toContain('alice@example.com');
  });

  it('shows Active badge for active users', async () => {
    listUsersMock.mockResolvedValue([ALICE]);
    const el = mount();
    await flushPromises();
    expect(el.innerHTML).toContain('badge-active');
    expect(el.textContent).toContain('Active');
  });

  it('shows Inactive badge for inactive users', async () => {
    listUsersMock.mockResolvedValue([BOB]);
    const el = mount();
    await flushPromises();
    expect(el.innerHTML).toContain('badge-inactive');
    expect(el.textContent).toContain('Inactive');
  });

  // ── Empty state ───────────────────────────────────────────────────────────

  it('shows a no-users message when the list is empty', async () => {
    listUsersMock.mockResolvedValue([]);
    const el = mount();
    await flushPromises();
    expect(el.textContent).toContain('No users found');
  });

  // ── Error state ───────────────────────────────────────────────────────────

  it('shows an error message and retry button on API failure', async () => {
    listUsersMock.mockRejectedValue(new Error('Network error'));
    const el = mount();
    await flushPromises();
    expect(el.textContent).toContain('Network error');
    expect(el.querySelector('[data-action="reload"]')).not.toBeNull();
  });

  // ── Role gating ───────────────────────────────────────────────────────────

  it('hides Create / Edit / Delete buttons for ROLE_USER', async () => {
    // default mock returns ROLE_USER (set in vi.mock above)
    listUsersMock.mockResolvedValue([ALICE]);
    const el = mount();
    await flushPromises();
    expect(el.querySelector('[data-action="create"]')).toBeNull();
    expect(el.querySelector('[data-action="edit"]')).toBeNull();
    expect(el.querySelector('[data-action="delete"]')).toBeNull();
  });

  it('shows Create / Edit / Delete buttons for ADMIN role', async () => {
    authProfileRef.realmRoles = ['ROLE_ADMIN'];
    listUsersMock.mockResolvedValue([ALICE]);

    const el = mount();
    await flushPromises();

    expect(el.querySelector('[data-action="create"]')).not.toBeNull();
    expect(el.querySelector('[data-action="edit"]')).not.toBeNull();
    expect(el.querySelector('[data-action="delete"]')).not.toBeNull();
  });

  // ── Toggle detail row ─────────────────────────────────────────────────────

  it('expands a detail row when username button is clicked', async () => {
    listUsersMock.mockResolvedValue([ALICE]);
    getUserByIdMock.mockResolvedValue(ALICE);
    const el = mount();
    await flushPromises();

    const toggleBtn = el.querySelector<HTMLButtonElement>('[data-action="toggle"]');
    expect(toggleBtn).not.toBeNull();
    toggleBtn!.click();
    await flushPromises();

    expect(el.querySelector('.detail-grid')).not.toBeNull();
    expect(el.textContent).toContain('First Name');
  });

  it('collapses the detail row on second click', async () => {
    listUsersMock.mockResolvedValue([ALICE]);
    getUserByIdMock.mockResolvedValue(ALICE);
    const el = mount();
    await flushPromises();

    const toggleBtn = () => el.querySelector<HTMLButtonElement>('[data-action="toggle"]')!;
    toggleBtn().click(); await flushPromises();
    expect(el.querySelector('.detail-grid')).not.toBeNull();

    toggleBtn().click(); await flushPromises();
    expect(el.querySelector('.detail-grid')).toBeNull();
  });

  // ── Pagination ────────────────────────────────────────────────────────────

  it('renders a pagination-bar after successful load', async () => {
    listUsersMock.mockResolvedValue([ALICE]);
    const el = mount();
    await flushPromises();
    expect(el.querySelector('pagination-bar')).not.toBeNull();
  });
});

