import { getKeycloak, login } from '../auth/keycloak';
import { createLogger, getCorrelationId } from '../logger/logger';

const logger = createLogger('HTTP');
const UNAUTHORIZED_STATUSES = new Set([401, 403]);

export class ApiHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiHttpError';
    this.status = status;
  }
}

/**
 * Refreshes the Keycloak token (if expiring within 30 s) and returns the
 * `Authorization: Bearer …` header.  Redirects to login on any failure.
 */
async function authHeader(): Promise<Record<string, string>> {
  const keycloak = getKeycloak();

  try {
    await keycloak.updateToken(30);
  } catch (error) {
    logger.error('Token refresh failed', {
      error: error instanceof Error ? error.message : String(error),
      correlationId: getCorrelationId(),
    });
    await login();
    throw new ApiHttpError(401, 'Session expired. Redirecting to login.');
  }

  if (!keycloak.token) {
    logger.warn('Missing access token after refresh', {
      correlationId: getCorrelationId(),
    });
    await login();
    throw new ApiHttpError(401, 'Missing access token. Redirecting to login.');
  }

  return { Authorization: `Bearer ${keycloak.token}` };
}

/**
 * Builds the complete set of request headers for every outbound call:
 *
 * - `Authorization: Bearer <token>`  — Keycloak JWT, auto-refreshed when expiring
 * - `X-Correlation-ID`               — frontend session trace ID forwarded to backend
 *                                       for Splunk log correlation (omitted when unset)
 * - `X-Request-ID`                   — unique UUID per request; allows backend to match
 *                                       a single request across distributed service logs
 * - `Accept: application/json`
 *
 * Callers may spread additional headers (e.g. Content-Type) on top.
 */
async function buildBaseHeaders(): Promise<Record<string, string>> {
  const auth = await authHeader();
  const corrId = getCorrelationId();
  return {
    Accept: 'application/json',
    ...auth,
    ...(corrId ? { 'X-Correlation-ID': corrId } : {}),
    'X-Request-ID': crypto.randomUUID(),
  };
}

export async function getJson<T>(path: string): Promise<T> {
  const headers = await buildBaseHeaders();
  const startTime = performance.now();

  logger.requestStart('GET', path);

  try {
    const response = await fetch(path, {
      method: 'GET',
      headers,
    });

    const duration = Math.round(performance.now() - startTime);

    if (UNAUTHORIZED_STATUSES.has(response.status)) {
      logger.requestError('GET', path, response.status, 'Unauthorized');
      await login();
      throw new ApiHttpError(response.status, 'Unauthorized. Redirecting to login.');
    }

    if (!response.ok) {
      let errorBody = '';
      try {
        errorBody = JSON.stringify(await response.clone().json());
      } catch {
        errorBody = await response.text();
      }
      logger.requestError('GET', path, response.status, errorBody);
      throw new ApiHttpError(response.status, `Request failed (${response.status}).`);
    }

    const data = (await response.json()) as T;
    logger.requestEnd('GET', path, response.status, duration);
    return data;
  } catch (error) {
    if (error instanceof ApiHttpError) throw error;
    const duration = Math.round(performance.now() - startTime);
    logger.error('getJson error', {
      path,
      error: error instanceof Error ? error.message : String(error),
      duration,
      correlationId: getCorrelationId(),
    });
    throw error;
  }
}

async function mutate<T>(method: string, path: string, body?: unknown): Promise<T | void> {
  const baseHeaders = await buildBaseHeaders();
  const isJson = body !== undefined;
  const startTime = performance.now();

  logger.requestStart(method, path);

  try {
    const response = await fetch(path, {
      method,
      headers: {
        ...(isJson ? { 'Content-Type': 'application/json' } : {}),
        ...baseHeaders,
      },
      body: isJson ? JSON.stringify(body) : undefined,
    });

    const duration = Math.round(performance.now() - startTime);

    if (UNAUTHORIZED_STATUSES.has(response.status)) {
      logger.requestError(method, path, response.status, 'Unauthorized');
      await login();
      throw new ApiHttpError(response.status, 'Unauthorized. Redirecting to login.');
    }

    if (!response.ok) {
      // Try to surface server validation message
      let serverMessage = `Request failed (${response.status}).`;
      let errorBody = '';
      try {
        const err = (await response.clone().json()) as { message?: string };
        if (err.message) serverMessage = err.message;
        errorBody = JSON.stringify(err);
      } catch {
        errorBody = await response.text();
      }
      logger.requestError(method, path, response.status, errorBody);
      throw new ApiHttpError(response.status, serverMessage);
    }

    if (response.status === 204 || response.headers.get('content-length') === '0') {
      logger.requestEnd(method, path, response.status, duration);
      return;
    }

    const data = (await response.json()) as T;
    logger.requestEnd(method, path, response.status, duration);
    return data;
  } catch (error) {
    if (error instanceof ApiHttpError) throw error;
    const duration = Math.round(performance.now() - startTime);
    logger.error(`${method} request error`, {
      path,
      error: error instanceof Error ? error.message : String(error),
      duration,
      correlationId: getCorrelationId(),
    });
    throw error;
  }
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('POST', path, body) as Promise<T>;
}

export function putJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('PUT', path, body) as Promise<T>;
}

/**
 * Sends an authenticated `PATCH` request.
 *
 * Use for partial updates (only changed fields) where the backend supports
 * `PATCH` semantics — e.g. `PATCH /api/v1/users/{id}`.
 */
export function patchJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('PATCH', path, body) as Promise<T>;
}

export function deleteVoid(path: string): Promise<void> {
  return mutate('DELETE', path) as Promise<void>;
}
