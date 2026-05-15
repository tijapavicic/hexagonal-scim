/**
 * Structured logger for frontend with request tracing.
 * Provides consistent logging across the application with optional correlation IDs.
 */

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogContext {
  level: LogLevel;
  timestamp: string;
  name: string;
  message: string;
  data?: Record<string, unknown>;
  traceId?: string;
  correlationId?: string;
  url?: string;
  status?: number;
  error?: string;
}

let correlationId: string | null = null;

/**
 * Generate a correlation ID for tracking related operations.
 */
export function generateCorrelationId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(7)}`;
}

/**
 * Set the current correlation ID for all subsequent logs.
 */
export function setCorrelationId(id: string | null): void {
  correlationId = id;
}

/**
 * Get the current correlation ID.
 */
export function getCorrelationId(): string | null {
  return correlationId;
}

/**
 * Format a log entry as JSON for easy parsing and filtering.
 */
function formatLogEntry(context: LogContext): string {
  return JSON.stringify(context);
}

/**
 * Log a message with structured data.
 */
function logStructured(level: LogLevel, name: string, message: string, data?: Record<string, unknown>): void {
  const context: LogContext = {
    level,
    timestamp: new Date().toISOString(),
    name,
    message,
    ...(data && Object.keys(data).length > 0 && { data }),
    ...(correlationId && { correlationId }),
  };

  const formatted = formatLogEntry(context);

  switch (level) {
    case 'debug':
      console.debug(formatted);
      break;
    case 'info':
      console.info(formatted);
      break;
    case 'warn':
      console.warn(formatted);
      break;
    case 'error':
      console.error(formatted);
      break;
  }
}

/**
 * Create a logger instance for a specific module.
 */
export function createLogger(name: string) {
  return {
    debug(message: string, data?: Record<string, unknown>): void {
      logStructured('debug', name, message, data);
    },

    info(message: string, data?: Record<string, unknown>): void {
      logStructured('info', name, message, data);
    },

    warn(message: string, data?: Record<string, unknown>): void {
      logStructured('warn', name, message, data);
    },

    error(message: string, data?: Record<string, unknown>): void {
      logStructured('error', name, message, data);
    },

    requestStart(method: string, url: string): void {
      const context: LogContext = {
        level: 'info',
        timestamp: new Date().toISOString(),
        name,
        message: `${method} request started`,
        url,
        ...(correlationId && { correlationId }),
      };
      console.info(formatLogEntry(context));
    },

    requestEnd(method: string, url: string, status: number, duration: number): void {
      const context: LogContext = {
        level: status >= 400 ? 'warn' : 'info',
        timestamp: new Date().toISOString(),
        name,
        message: `${method} request completed`,
        url,
        status,
        data: { duration: `${duration}ms` },
        ...(correlationId && { correlationId }),
      };
      console.info(formatLogEntry(context));
    },

    requestError(method: string, url: string, status: number, error: string): void {
      const context: LogContext = {
        level: 'error',
        timestamp: new Date().toISOString(),
        name,
        message: `${method} request failed`,
        url,
        status,
        error,
        ...(correlationId && { correlationId }),
      };
      console.error(formatLogEntry(context));
    },
  };
}

