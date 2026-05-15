import { getKeycloak, login } from '../auth/keycloak';

const UNAUTHORIZED_STATUSES = new Set([401, 403]);

export class ApiHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiHttpError';
    this.status = status;
  }
}

async function authHeader(): Promise<Record<string, string>> {
  const keycloak = getKeycloak();

  try {
    await keycloak.updateToken(30);
  } catch {
    await login();
    throw new ApiHttpError(401, 'Session expired. Redirecting to login.');
  }

  if (!keycloak.token) {
    await login();
    throw new ApiHttpError(401, 'Missing access token. Redirecting to login.');
  }

  return { Authorization: `Bearer ${keycloak.token}` };
}

export async function getJson<T>(path: string): Promise<T> {
  const headers = await authHeader();

  const response = await fetch(path, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      ...headers,
    },
  });

  if (UNAUTHORIZED_STATUSES.has(response.status)) {
    await login();
    throw new ApiHttpError(response.status, 'Unauthorized. Redirecting to login.');
  }

  if (!response.ok) {
    throw new ApiHttpError(response.status, `Request failed (${response.status}).`);
  }

  return (await response.json()) as T;
}

async function mutate<T>(method: string, path: string, body?: unknown): Promise<T | void> {
  const headers = await authHeader();
  const isJson = body !== undefined;

  const response = await fetch(path, {
    method,
    headers: {
      ...(isJson ? { 'Content-Type': 'application/json' } : {}),
      Accept: 'application/json',
      ...headers,
    },
    body: isJson ? JSON.stringify(body) : undefined,
  });

  if (UNAUTHORIZED_STATUSES.has(response.status)) {
    await login();
    throw new ApiHttpError(response.status, 'Unauthorized. Redirecting to login.');
  }

  if (!response.ok) {
    // Try to surface server validation message
    let serverMessage = `Request failed (${response.status}).`;
    try {
      const err = (await response.json()) as { message?: string };
      if (err.message) serverMessage = err.message;
    } catch { /* non-JSON body */ }
    throw new ApiHttpError(response.status, serverMessage);
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return;
  }
  return (await response.json()) as T;
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('POST', path, body) as Promise<T>;
}

export function putJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('PUT', path, body) as Promise<T>;
}

export function deleteVoid(path: string): Promise<void> {
  return mutate('DELETE', path) as Promise<void>;
}

