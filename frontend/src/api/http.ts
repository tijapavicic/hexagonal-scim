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

