import { describe, it, expect, vi, beforeEach } from 'vitest';

// ── Hoist mocks before module import ─────────────────────────────────────────
const { getJsonMock, postJsonMock, putJsonMock, deleteVoidMock } = vi.hoisted(() => ({
  getJsonMock:    vi.fn(),
  postJsonMock:   vi.fn(),
  putJsonMock:    vi.fn(),
  deleteVoidMock: vi.fn(),
}));

vi.mock('./http', () => ({
  getJson:    getJsonMock,
  postJson:   postJsonMock,
  putJson:    putJsonMock,
  deleteVoid: deleteVoidMock,
  ApiHttpError: class ApiHttpError extends Error {
    status: number;
    constructor(status: number, msg: string) { super(msg); this.status = status; }
  },
}));

import { listUsers, getUserById, getUserByKeycloakId, createUser, updateUser, deleteUser } from './users';
import type { UserDto } from '../types/user.dto';
import { UserRole } from '../types/user.dto';

// ── Fixture ───────────────────────────────────────────────────────────────────
const alice: UserDto = {
  id: 1,
  email: 'alice@example.com',
  displayName: 'Alice Smith',
  role: UserRole.BUYER,
  keycloakId: 'a59ba86d-5c3c-42f3-b15c-a54e623fbb64'
};
const bob: UserDto = {
  id: 2,
  email: 'bob@example.com',
  displayName: 'Bob Jones',
  role: UserRole.SELLER
};

describe('api/users', () => {
  beforeEach(() => vi.clearAllMocks());

  // ── listUsers ───────────────────────────────────────────────────────────────
  describe('listUsers', () => {
    it('calls GET /api/v1/users with page and size', async () => {
      getJsonMock.mockResolvedValue([alice, bob]);
      const result = await listUsers(0, 10);
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/users?page=0&size=10');
      expect(result).toEqual([alice, bob]);
    });

    it('uses defaults page=0 size=10 when called without arguments', async () => {
      getJsonMock.mockResolvedValue([]);
      await listUsers();
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/users?page=0&size=10');
    });

    it('forwards paginated request params', async () => {
      getJsonMock.mockResolvedValue([bob]);
      await listUsers(2, 25);
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/users?page=2&size=25');
    });

    it('propagates ApiHttpError on failure', async () => {
      getJsonMock.mockRejectedValue(new Error('Request failed (403).'));
      await expect(listUsers()).rejects.toThrow('403');
    });
  });

  // ── getUserById ─────────────────────────────────────────────────────────────
  describe('getUserById', () => {
    it('calls GET /api/v1/users/{id}', async () => {
      getJsonMock.mockResolvedValue(alice);
      const result = await getUserById(1);
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/users/1');
      expect(result).toEqual(alice);
    });

    it('propagates error when user not found', async () => {
      getJsonMock.mockRejectedValue(new Error('Request failed (404).'));
      await expect(getUserById(999)).rejects.toThrow('404');
    });
  });

  // ── getUserByKeycloakId ─────────────────────────────────────────────────────
  describe('getUserByKeycloakId', () => {
    it('calls GET /api/v1/users/by-keycloak-id/{keycloakId}', async () => {
      getJsonMock.mockResolvedValue(alice);
      const result = await getUserByKeycloakId('a59ba86d-5c3c-42f3-b15c-a54e623fbb64');
      expect(getJsonMock).toHaveBeenCalledWith('/api/v1/users/by-keycloak-id/a59ba86d-5c3c-42f3-b15c-a54e623fbb64');
      expect(result).toEqual(alice);
    });

    it('propagates error when user not found by keycloak ID', async () => {
      getJsonMock.mockRejectedValue(new Error('Request failed (404).'));
      await expect(getUserByKeycloakId('nonexistent-uuid')).rejects.toThrow('404');
    });
  });

  // ── createUser ──────────────────────────────────────────────────────────────
  describe('createUser', () => {
    it('calls POST /api/v1/users with body', async () => {
      postJsonMock.mockResolvedValue({ ...alice, id: 10 });
      const body = { email: 'alice@example.com', displayName: 'Alice Smith' };
      const result = await createUser(body);
      expect(postJsonMock).toHaveBeenCalledWith('/api/v1/users', body);
      expect(result.id).toBe(10);
    });

    it('propagates 409 conflict', async () => {
      postJsonMock.mockRejectedValue(new Error('Email already exists.'));
      await expect(createUser({ email: 'duplicate@example.com', displayName: 'Test' }))
        .rejects.toThrow('already exists');
    });
  });

  // ── updateUser ──────────────────────────────────────────────────────────────
  describe('updateUser', () => {
    it('calls PUT /api/v1/users/{id} with body', async () => {
      putJsonMock.mockResolvedValue({ ...alice, displayName: 'Alicia Smith' });
      const body = { email: 'alice@example.com', displayName: 'Alicia Smith' };
      const result = await updateUser(1, body);
      expect(putJsonMock).toHaveBeenCalledWith('/api/v1/users/1', body);
      expect(result.displayName).toBe('Alicia Smith');
    });
  });

  // ── deleteUser ──────────────────────────────────────────────────────────────
  describe('deleteUser', () => {
    it('calls DELETE /api/v1/users/{id}', async () => {
      deleteVoidMock.mockResolvedValue(undefined);
      await deleteUser(1);
      expect(deleteVoidMock).toHaveBeenCalledWith('/api/v1/users/1');
    });

    it('propagates error on 404', async () => {
      deleteVoidMock.mockRejectedValue(new Error('Request failed (404).'));
      await expect(deleteUser(999)).rejects.toThrow('404');
    });
  });
});

