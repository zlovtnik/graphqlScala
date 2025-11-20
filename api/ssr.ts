import { VercelRequest, VercelResponse } from '@vercel/node';
import type { Response as ExpressResponse } from 'express';
import { IncomingMessage } from 'node:http';
import validator from 'validator';
import { app } from '../frontend/dist/frontend/server/server.mjs';

const ssrApp = app();
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX = parseInt(process.env['RATE_LIMIT_MAX'] ?? '100', 10);
const SSR_TIMEOUT_MS = Math.min(
  parseInt(process.env['SSR_TIMEOUT_MS'] ?? '25000', 10),
  29_000
);
const allowedHostsEnv = process.env['ALLOWED_HOSTS'] ?? '';
const allowedHosts = allowedHostsEnv
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);

type RateLimitEntry = {
  count: number;
  resetAt: number;
};

const rateLimitStore = new Map<string, RateLimitEntry>();

function getClientIp(req: VercelRequest): string {
  const xForwardedFor = req.headers['x-forwarded-for'];
  if (typeof xForwardedFor === 'string' && xForwardedFor.length > 0) {
    return xForwardedFor.split(',')[0]?.trim() ?? 'unknown';
  }
  if (Array.isArray(xForwardedFor) && xForwardedFor.length > 0) {
    return xForwardedFor[0];
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const socket = (req as any).socket as IncomingMessage['socket'] | undefined;
  return socket?.remoteAddress ?? (req as unknown as { ip?: string }).ip ?? 'unknown';
}

function enforceRateLimit(ip: string): { allowed: boolean; retryAfterSec: number } {
  const now = Date.now();
  const existing = rateLimitStore.get(ip);
  if (!existing || existing.resetAt <= now) {
    rateLimitStore.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
    return { allowed: true, retryAfterSec: 0 };
  }

  existing.count += 1;
  if (existing.count > RATE_LIMIT_MAX) {
    const retryAfter = Math.ceil((existing.resetAt - now) / 1000);
    return { allowed: false, retryAfterSec: retryAfter };
  }

  return { allowed: true, retryAfterSec: 0 };
}

function isValidHostname(hostname: string): boolean {
  return validator.isFQDN(hostname, { require_tld: false });
}

function isValidPort(port: string): boolean {
  const portNum = Number.parseInt(port, 10);
  return Number.isInteger(portNum) && portNum >= 1 && portNum <= 65535;
}

function isValidHostHeader(hostHeader: string): boolean {
  if (!hostHeader) return false;
  const parts = hostHeader.split(':');
  if (parts.length > 2) return false;
  const [hostname, port] = parts;
  if (!isValidHostname(hostname)) return false;
  if (port !== undefined && !isValidPort(port)) return false;
  return true;
}

function validateHostHeader(hostHeader: string, clientIp: string): void {
  if (!hostHeader) {
    throw new Error(`Missing Host header from ${clientIp}`);
  }
  if (!isValidHostHeader(hostHeader)) {
    throw new Error(`Invalid Host header: ${hostHeader}`);
  }
  if (allowedHosts.length > 0 && !allowedHosts.includes(hostHeader)) {
    throw new Error(`Host not in allowlist: ${hostHeader}`);
  }
}

function createExpressResponseAdapter(res: VercelResponse): ExpressResponse {
  const adapter = res as unknown as ExpressResponse & VercelResponse;

  adapter.status = ((code: number) => {
    res.status(code);
    return adapter;
  }) as ExpressResponse['status'];

  adapter.send = ((body?: any) => {
    res.send(body);
    return adapter;
  }) as ExpressResponse['send'];

  adapter.json = ((body?: any) => {
    res.json(body);
    return adapter;
  }) as ExpressResponse['json'];

  adapter.end = ((chunk?: any) => {
    res.end(chunk);
    return adapter;
  }) as ExpressResponse['end'];

  adapter.write = ((chunk: any) => res.write(chunk)) as ExpressResponse['write'];

  adapter.set = ((field: any, value?: any) => {
    if (typeof field === 'string') {
      res.setHeader(field, value);
    } else if (typeof field === 'object' && field !== null) {
      Object.entries(field).forEach(([key, val]) => res.setHeader(key, val as any));
    }
    return adapter;
  }) as ExpressResponse['set'];

  adapter.get = ((field: string) => res.getHeader(field) as string) as ExpressResponse['get'];

  adapter.setHeader = ((name: string, value: any) => {
    res.setHeader(name, value);
    return adapter;
  }) as unknown as ExpressResponse['setHeader'];

  adapter.getHeader = ((name: string) => res.getHeader(name)) as unknown as ExpressResponse['getHeader'];

  adapter.removeHeader = ((name: string) => {
    res.removeHeader(name);
    return adapter;
  }) as unknown as ExpressResponse['removeHeader'];

  adapter.header = adapter.set;

  adapter.type = ((type: string) => {
    res.setHeader('Content-Type', type);
    return adapter;
  }) as ExpressResponse['type'];

  adapter.redirect = ((statusOrUrl: number | string, maybeUrl?: string) => {
    if (typeof statusOrUrl === 'number') {
      if (!maybeUrl) {
        throw new Error('URL is required when providing a redirect status code');
      }
      res.status(statusOrUrl);
      res.setHeader('Location', maybeUrl);
    } else {
      res.status(302);
      res.setHeader('Location', statusOrUrl);
    }
    res.end();
    return adapter;
  }) as ExpressResponse['redirect'];

  adapter.location = ((url: string) => {
    res.setHeader('Location', url);
    return adapter;
  }) as ExpressResponse['location'];

  adapter.append = ((field: string, value: any) => {
    const current = res.getHeader(field);
    const nextValue = current
      ? ([] as any[]).concat(current as any, value)
      : value;
    res.setHeader(field, nextValue);
    return adapter;
  }) as ExpressResponse['append'];

  return adapter;
}

export default async function handler(
  req: VercelRequest,
  res: VercelResponse
): Promise<void> {
  const start = Date.now();
  const clientIp = getClientIp(req);
  const method = req.method ?? 'GET';
  const originalUrl = req.url ?? req.headers['x-vercel-original-path'] ?? '/';
  const hostHeader = String(req.headers.host ?? '').trim();

  try {
    const rateLimitResult = enforceRateLimit(clientIp);
    if (!rateLimitResult.allowed) {
      res.setHeader('Retry-After', String(rateLimitResult.retryAfterSec));
      res.status(429).json({
        error: 'Too many requests',
        message: 'Rate limit exceeded. Try again later.',
        retryAfterSeconds: rateLimitResult.retryAfterSec,
      });
      return;
    }

    validateHostHeader(hostHeader, clientIp);

    console.info(
      JSON.stringify({
        level: 'info',
        event: 'ssr_request_start',
        method,
        originalUrl,
        host: hostHeader,
        clientIp,
        timestamp: new Date().toISOString(),
      })
    );

    const expressRes = createExpressResponseAdapter(res);

    const renderPromise = new Promise<void>((resolve, reject) => {
      const cleanup = () => {
        res.off('finish', onFinish);
        res.off('close', onClose);
        res.off('error', onError);
      };

      const onFinish = () => {
        cleanup();
        resolve();
      };

      const onClose = () => {
        cleanup();
        resolve();
      };

      const onError = (err: Error) => {
        cleanup();
        reject(err);
      };

      res.on('finish', onFinish);
      res.on('close', onClose);
      res.on('error', onError);

      ssrApp.handle(req as any, expressRes as any, (err?: unknown) => {
        if (err) {
          cleanup();
          reject(err instanceof Error ? err : new Error(String(err)));
        }
      });
    });

    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error('SSR_RENDER_TIMEOUT')), SSR_TIMEOUT_MS);
    });

    await Promise.race([renderPromise, timeoutPromise]);

    console.info(
      JSON.stringify({
        level: 'info',
        event: 'ssr_request_complete',
        durationMs: Date.now() - start,
        method,
        originalUrl,
        host: hostHeader,
        clientIp,
        timestamp: new Date().toISOString(),
      })
    );
  } catch (error) {
    const err = error instanceof Error ? error : new Error(String(error));
    const responseBody = {
      error: 'SSR_RENDER_ERROR',
      message: 'An unexpected error occurred while rendering the page.',
    };

    console.error(
      JSON.stringify({
        level: 'error',
        event: 'ssr_request_error',
        method,
        originalUrl,
        host: hostHeader,
        clientIp,
        durationMs: Date.now() - start,
        timestamp: new Date().toISOString(),
        details: {
          message: err.message,
          stack: err.stack,
          name: err.name,
        },
      })
    );

    if (!res.headersSent) {
      res.status(err.message === 'SSR_RENDER_TIMEOUT' ? 504 : 500).json(responseBody);
    }
  }
}
