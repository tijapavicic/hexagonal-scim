/**
 * Unit tests for the authenticated HTTP client (http.ts).
 *
 * Covers:
 * - Bearer token injection on every HTTP method (GET / POST / PUT / PATCH / DELETE)
 * - Token refresh before every request (updateToken called with min-validity = 30)
 * - Token refresh failure → login() called + ApiHttpError(401) thrown
 * - Missing token after refresh → login() called + ApiHttpError(401) thrown
 * - 401 / 403 responses → login() called + ApiHttpError thrown
 * - Non-2xx responses → ApiHttpError with server message surfaced
 * - 204 No-Content → void returned without body parsing
 * - X-Correlation-ID forwarded when set; omitted when null
 * - X-Request-ID always sent (unique UUID per request)
 * - Content-Type: application/json sent on mutating requests (POST / PUT / PATCH)
 * - Content-Type NOT sent on DELETE (no body)
 * - patchJson delegates to PATCH method with correct URL and body
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

// ── Hoisted mock factories — evaluated before any import ──────────────────────

const mockKeycloak = vi.hoisted(() => ({
  token: 'test-access-token' as string | undefined | null,
  updateToken: vi.fn<[number], Promise<boolean>>().mockResolvedValue(true),
}));

const mockLogin = vi.hoisted(() => vi.fn<[], Promise<void>>().mockResolvedValue(undefined));

const mockGetCorrelationId = vi.hoisted(
  () => vi.fn<[], string | null>().mockReturnValue(null),
);

vi.mock('../auth/keycloak', () => ({
  getKeycloak: () => mockKeycloak,
  login: mockLogin,
}));

vi.mock('../logger/logger', () => ({
  createLogger: () => ({
    requestStart: vi.fn(),
    requestEnd: vi.fn(),
    requestError: vi.fn(),
    error: vi.fn(),
    warn: vi.fn(),
    info: vi.fn(),
  }),
  getCorrelationId: mockGetCorrelationId,
}));

// ── Module under test ─────────────────────────────────────────────────────────

import { getJson, postJson, putJson, patchJson, deleteVoid, ApiHttpError } from './http';

// ── Helpers ───────────────────────────────────────────────────────────────────

const FIXED_UUID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

function mockFetchOk(body: unknown, status = 200): void {
  const headers = new Headers({ 'content-type': 'application/json' });
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      status,
      headers,
      json: () => Promise.resolve(body),
      clone: () => ({ json: () => Promise.resolve(body), text: () => Promise.resolve('') }),
      text: () => Promise.resolve(''),
    }),
  );
}

function mockFetchStatus(status: number, body?: unknown): void {
  const headers = new Headers({ 'content-length': '0' });
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      headers,
      json: () => Promise.resolve(body ?? {}),
      clone: () => ({
        json: () => Promise.resolve(body ?? {}),
        text: () => Promise.resolve(JSON.stringify(body ?? {})),
      }),
      text: () => Promise.resolve(JSON.stringify(body ?? {})),
    }),
  );
}

function mockFetchNoContent(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers({ 'content-length': '0' }),
      json: () => Promise.reject(new Error('no body')),
      clone: () => ({ json: () => Promise.reject(new Error('no body')), text: () => Promise.resolve('') }),
      text: () => Promise.resolve(''),
    }),
  );
}

function capturedHeaders(): Record<string, string> {
  const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
  return call[1].headers as Record<string, string>;
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  mockKeycloak.token = 'test-access-token';
  mockKeycloak.updateToken.mockResolvedValue(true);
  mockLogin.mockResolvedValue(undefined);
  mockGetCorrelationId.mockReturnValue(null);

  // Stub crypto.randomUUID to a deterministic value for assertion
  vi.stubGlobal('crypto', { randomUUID: vi.fn().mockReturnValue(FIXED_UUID) });
});

// ── Bearer token injection ────────────────────────────────────────────────────

describe('Bearer token injection', () => {
  it('getJson attaches Authorization: Bearer header', async () => {
    mockFetchOk({ id: 1 });
    await getJson('/api/v1/users/1');
    expect(capturedHeaders()).toMatchObject({
      Authorization: 'Bearer test-access-token',
    });
  });

  it('postJson attaches Authorization: Bearer header', async () => {
    mockFetchOk({ id: 2 });
    await postJson('/api/v1/users', { email: 'a@b.com' });
    expect(capturedHeaders()).toMatchObject({
      Authorization: 'Bearer test-access-token',
    });
  });

  it('putJson attaches Authorization: Bearer header', async () => {
    mockFetchOk({ id: 1 });
    await putJson('/api/v1/users/1', { email: 'a@b.com', displayName: 'A' });
    expect(capturedHeaders()).toMatchObject({
      Authorization: 'Bearer test-access-token',
    });
  });

  it('patchJson attaches Authorization: Bearer header', async () => {
    mockFetchOk({ id: 1 });
    await patchJson('/api/v1/users/1', { displayName: 'B' });
    expect(capturedHeaders()).toMatchObject({
      Authorization: 'Bearer test-access-token',
    });
  });

  it('deleteVoid attaches Authorization: Bearer header', async () => {
    mockFetchNoContent();
    await deleteVoid('/api/v1/users/1');
    expect(capturedHeaders()).toMatchObject({
      Authorization: 'Bearer test-access-token',
    });
  });
});

// ── Token refresh ─────────────────────────────────────────────────────────────

describe('token refresh', () => {
  it('calls updateToken(30) before every request', async () => {
    mockFetchOk({});
    await getJson('/api/v1/users');
    expect(mockKeycloak.updateToken).toHaveBeenCalledWith(30);
  });

  it('calls updateToken before POST', async () => {
    mockFetchOk({ id: 1 });
    await postJson('/api/v1/users', {});
    expect(mockKeycloak.updateToken).toHaveBeenCalledWith(30);
  });

  it('throws ApiHttpError(401) and calls login() when updateToken rejects', async () => {
    mockKeycloak.updateToken.mockRejectedValue(new Error('Refresh failed'));
    mockFetchOk({});

    await expect(getJson('/api/v1/users')).rejects.toMatchObject({
      status: 401,
      message: 'Session expired. Redirecting to login.',
    });
    expect(mockLogin).toHaveBeenCalledTimes(1);
  });

  it('throws ApiHttpError(401) and calls login() when token is null after refresh', async () => {
    mockKeycloak.token = null;
    mockFetchOk({});

    await expect(getJson('/api/v1/users')).rejects.toMatchObject({
      status: 401,
      message: 'Missing access token. Redirecting to login.',
    });
    expect(mockLogin).toHaveBeenCalledTimes(1);
  });

  it('throws ApiHttpError(401) and calls login() when token is empty string', async () => {
    mockKeycloak.token = '';
    mockFetchOk({});

    await expect(getJson('/api/v1/users')).rejects.toMatchObject({
      status: 401,
    });
    expect(mockLogin).toHaveBeenCalledTimes(1);
  });
});

// ── HTTP method routing ───────────────────────────────────────────────────────

describe('HTTP method routing', () => {
  it('getJson sends GET request', async () => {
    mockFetchOk([]);
    await getJson('/api/v1/users');
    const fetchCall = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(fetchCall[1].method).toBe('GET');
    expect(fetchCall[0]).toBe('/api/v1/users');
  });

  it('postJson sends POST with JSON body', async () => {
    mockFetchOk({ id: 1 });
    await postJson('/api/v1/users', { email: 'x@y.com' });
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('POST');
    expect(call[1].body).toBe(JSON.stringify({ email: 'x@y.com' }));
    expect((call[1].headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('putJson sends PUT with JSON body', async () => {
    mockFetchOk({ id: 1 });
    await putJson('/api/v1/users/1', { email: 'x@y.com', displayName: 'X' });
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('PUT');
    expect((call[1].headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('patchJson sends PATCH with JSON body', async () => {
    mockFetchOk({ id: 1 });
    await patchJson('/api/v1/users/1', { displayName: 'New Name' });
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('PATCH');
    expect(call[1].body).toBe(JSON.stringify({ displayName: 'New Name' }));
    expect((call[1].headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('deleteVoid sends DELETE without body', async () => {
    mockFetchNoContent();
    await deleteVoid('/api/v1/users/1');
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('DELETE');
    expect(call[1].body).toBeUndefined();
    // DELETE has no body, so Content-Type should not be present
    expect((call[1].headers as Record<string, string>)['Content-Type']).toBeUndefined();
  });
});

// ── X-Correlation-ID header ───────────────────────────────────────────────────

describe('X-Correlation-ID header', () => {
  it('sends X-Correlation-ID when correlationId is set', async () => {
    mockGetCorrelationId.mockReturnValue('my-correlation-id');
    mockFetchOk({});
    await getJson('/api/v1/users');
    expect(capturedHeaders()).toMatchObject({
      'X-Correlation-ID': 'my-correlation-id',
    });
  });

  it('omits X-Correlation-ID when correlationId is null', async () => {
    mockGetCorrelationId.mockReturnValue(null);
    mockFetchOk({});
    await getJson('/api/v1/users');
    expect(capturedHeaders()).not.toHaveProperty('X-Correlation-ID');
  });

  it('forwards X-Correlation-ID on POST requests', async () => {
    mockGetCorrelationId.mockReturnValue('trace-abc-123');
    mockFetchOk({ id: 1 });
    await postJson('/api/v1/payments', { productId: 1, quantity: 1 });
    expect(capturedHeaders()).toMatchObject({
      'X-Correlation-ID': 'trace-abc-123',
    });
  });

  it('forwards X-Correlation-ID on PATCH requests', async () => {
    mockGetCorrelationId.mockReturnValue('trace-patch-456');
    mockFetchOk({ id: 1 });
    await patchJson('/api/v1/users/1', { displayName: 'Test' });
    expect(capturedHeaders()).toMatchObject({
      'X-Correlation-ID': 'trace-patch-456',
    });
  });
});

// ── X-Request-ID header ───────────────────────────────────────────────────────

describe('X-Request-ID header', () => {
  it('sends X-Request-ID on every request', async () => {
    mockFetchOk({});
    await getJson('/api/v1/users');
    expect(capturedHeaders()).toMatchObject({
      'X-Request-ID': FIXED_UUID,
    });
  });

  it('sends X-Request-ID on POST', async () => {
    mockFetchOk({ id: 1 });
    await postJson('/api/v1/users', {});
    expect(capturedHeaders()).toMatchObject({ 'X-Request-ID': FIXED_UUID });
  });

  it('sends X-Request-ID on DELETE', async () => {
    mockFetchNoContent();
    await deleteVoid('/api/v1/users/1');
    expect(capturedHeaders()).toMatchObject({ 'X-Request-ID': FIXED_UUID });
  });
});

// ── 401 / 403 response handling ───────────────────────────────────────────────

describe('unauthorized response handling', () => {
  it('throws ApiHttpError(401) and calls login() on 401 response', async () => {
    mockFetchStatus(401);

    await expect(getJson('/api/v1/users')).rejects.toMatchObject({
      status: 401,
      message: 'Unauthorized. Redirecting to login.',
    });
    expect(mockLogin).toHaveBeenCalled();
  });

  it('throws ApiHttpError(403) and calls login() on 403 response', async () => {
    mockFetchStatus(403);

    await expect(getJson('/api/v1/users')).rejects.toMatchObject({
      status: 403,
    });
    expect(mockLogin).toHaveBeenCalled();
  });

  it('throws ApiHttpError(403) on 403 from POST', async () => {
    mockFetchStatus(403);

    await expect(postJson('/api/v1/users', {})).rejects.toMatchObject({
      status: 403,
    });
    expect(mockLogin).toHaveBeenCalled();
  });
});

// ── Non-2xx error handling ────────────────────────────────────────────────────

describe('non-2xx error handling', () => {
  it('throws ApiHttpError(404) for GET not found', async () => {
    mockFetchStatus(404, { message: 'User not found' });

    await expect(getJson('/api/v1/users/999')).rejects.toMatchObject({
      status: 404,
      message: 'Request failed (404).',
    });
  });

  it('surfaces server validation message from POST 400 body', async () => {
    mockFetchStatus(400, { message: 'Email already exists' });

    await expect(postJson('/api/v1/users', { email: 'dup@x.com' })).rejects.toMatchObject({
      status: 400,
      message: 'Email already exists',
    });
  });

  it('falls back to generic message when server body has no message field', async () => {
    mockFetchStatus(500, { error: 'internal' });

    await expect(putJson('/api/v1/users/1', {})).rejects.toMatchObject({
      status: 500,
      message: 'Request failed (500).',
    });
  });

  it('surfaces server message on PATCH 400', async () => {
    mockFetchStatus(400, { message: 'At least one field must be provided' });

    await expect(patchJson('/api/v1/users/1', {})).rejects.toMatchObject({
      status: 400,
      message: 'At least one field must be provided',
    });
  });
});

// ── 204 No-Content ────────────────────────────────────────────────────────────

describe('204 No-Content', () => {
  it('deleteVoid resolves to void on 204', async () => {
    mockFetchNoContent();
    const result = await deleteVoid('/api/v1/users/1');
    expect(result).toBeUndefined();
  });
});

// ── Accept header ─────────────────────────────────────────────────────────────

describe('Accept header', () => {
  it('sends Accept: application/json on GET', async () => {
    mockFetchOk({});
    await getJson('/api/v1/users');
    expect(capturedHeaders()).toMatchObject({ Accept: 'application/json' });
  });

  it('sends Accept: application/json on POST', async () => {
    mockFetchOk({ id: 1 });
    await postJson('/api/v1/users', {});
    expect(capturedHeaders()).toMatchObject({ Accept: 'application/json' });
  });
});

// ── ApiHttpError ──────────────────────────────────────────────────────────────

describe('ApiHttpError', () => {
  it('is an instance of Error', () => {
    const err = new ApiHttpError(422, 'Validation failed');
    expect(err).toBeInstanceOf(Error);
    expect(err.name).toBe('ApiHttpError');
    expect(err.status).toBe(422);
    expect(err.message).toBe('Validation failed');
  });
});

