import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine } from '@angular/ssr';
import express, { Request, Response, NextFunction } from 'express';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import bootstrap from './src/main.server';
import validator from 'validator';
import rateLimit from 'express-rate-limit';
// Custom error class for SSR render timeouts
class SSRTimeoutError extends Error {
  constructor() {
    super('SSR render timeout');
    this.name = 'SSRTimeoutError';
    Object.setPrototypeOf(this, SSRTimeoutError.prototype);
  }
}

// The Express app is exported so that it can be used by serverless Functions.
export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const indexHtml = join(serverDistFolder, 'index.server.html');

  const commonEngine = new CommonEngine();

  server.set('view engine', 'html');
  server.set('views', browserDistFolder);

  // Configure Express to trust proxy (important for getting correct client IP behind load balancers)
  // Set to 1 if behind a single proxy, or adjust based on your infrastructure
  const trustProxyConfig = process.env['TRUST_PROXY'] || (process.env['NODE_ENV'] === 'production' ? '1' : false);
  server.set('trust proxy', trustProxyConfig);

  // Configure rate limiting middleware for SSR endpoint protection
  const limiter = rateLimit({
    windowMs: 60 * 1000, // 1 minute window
    max: parseInt(process.env['RATE_LIMIT_MAX'] || '100', 10), // max requests per IP (default 100)
    message: 'Too many requests from this IP address, please try again after a minute.',
    standardHeaders: true, // Return rate limit info in the `RateLimit-*` headers
    legacyHeaders: false, // Disable the `X-RateLimit-*` headers
    // Skip rate limiting for health checks or specific paths if needed
    skip: (req: Request): boolean => {
      const path = req.originalUrl.split('?')[0];
      return path === '/health' || path === '/healthcheck';
    },
    // Custom handler for rate limit exceeded
    handler: (req: Request, res: Response, next?: NextFunction): void => {
      const path = req.originalUrl.split('?')[0];
      console.warn(`Rate limit exceeded for IP: ${req.ip}`, {
        path: path,
        method: req.method,
        userAgent: req.headers['user-agent'],
        timestamp: new Date().toISOString(),
      });
      res.status(429).json({
        error: 'Too many requests',
        message: 'You have exceeded the rate limit. Please try again after a minute.',
        retryAfter: 60,
      });
    },
  });

  // Apply rate limiting middleware to protect against DoS attacks
  server.use(limiter);

  // Example Express Rest API endpoints
  // server.get('/api/**', (req, res) => { });
  // Serve static files from /browser
  server.use(express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
  }));

  const allowedHostsEnv = process.env['ALLOWED_HOSTS'] ?? '';
  const allowedHosts = allowedHostsEnv
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  // Hostname validation using 'validator' library (RFC 1123)


  function isValidHostname(hostname: string): boolean {
    return validator.isFQDN(hostname, { require_tld: false });
  }

  // Port validation (1-65535)
  function isValidPort(port: string): boolean {
    const portNum = parseInt(port, 10);
    return portNum >= 1 && portNum <= 65535;
  }

  // Combined host:port validation
  function isValidHostHeader(hostHeader: string): boolean {
    if (!hostHeader) return false;

    const parts = hostHeader.split(':');
    if (parts.length > 2) return false; // Too many colons

    const hostname = parts[0];
    const port = parts[1];

    // Validate hostname
    if (!isValidHostname(hostname)) return false;

    // Validate port if present
    if (port !== undefined && !isValidPort(port)) return false;

    return true;
  }

  // All regular routes use the Angular engine
  server.get('**', (req: any, res: any, next: any) => {
    const { protocol, originalUrl, baseUrl, headers } = req;
    const hostHeader = String(headers.host ?? '').trim();
    const clientIp = req.ip || req.socket.remoteAddress || 'unknown';

    // Check for missing Host header
    if (!hostHeader) {
      console.warn(`Rejected request: Missing Host header`, {
        clientIp,
        method: req.method,
        path: req.path,
        userAgent: headers['user-agent'],
      });
      res.status(400).send('Invalid host header');
      return;
    }

    // Check for invalid Host header format
    if (!isValidHostHeader(hostHeader)) {
      console.warn(`Rejected request: Invalid Host header format`, {
        hostHeader,
        clientIp,
        method: req.method,
        path: req.path,
        userAgent: headers['user-agent'],
      });
      res.status(400).send('Invalid host header');
      return;
    }

    // Check if Host header is in allowlist (if allowlist is enabled)
    if (allowedHosts.length > 0 && !allowedHosts.includes(hostHeader)) {
      console.warn(`Rejected request: Host header not in allowlist`, {
        hostHeader,
        allowedHosts,
        clientIp,
        method: req.method,
        path: req.path,
        userAgent: headers['user-agent'],
      });
      res.status(400).send('Invalid host header');
      return;
    }

    const renderPromise = commonEngine.render({
      bootstrap,
      documentFilePath: indexHtml,
      url: `${protocol}://${hostHeader}${originalUrl}`,
      publicPath: browserDistFolder,
      providers: [{ provide: APP_BASE_HREF, useValue: baseUrl }],
    });

    // Add timeout to prevent hanging renders (30 seconds)
    let timeoutId: NodeJS.Timeout | undefined = undefined;
    const timeoutPromise = new Promise<never>((_, reject) => {
      timeoutId = setTimeout(() => reject(new SSRTimeoutError()), 30000);
    });

    Promise.race([renderPromise, timeoutPromise])
      .then((html) => {
        clearTimeout(timeoutId);
        res.send(html);
      })
      .catch((err) => {
        clearTimeout(timeoutId);
        const isTimeout = err instanceof SSRTimeoutError;
        const errorContext = {
          error: {
            message: err.message,
            stack: err.stack,
            name: err.name,
          },
          request: {
            method: req.method,
            originalUrl: req.originalUrl,
            baseUrl: req.baseUrl,
            protocol: req.protocol,
            host: hostHeader,
            userAgent: req.headers['user-agent'],
            accept: req.headers.accept,
            fullUrl: `${req.protocol}://${hostHeader}${req.originalUrl}`,
          },
          providers: {
            baseHref: baseUrl,
            documentFilePath: indexHtml,
            publicPath: browserDistFolder,
          },
          timestamp: new Date().toISOString(),
          isTimeout,
        };

        console.error('SSR render error:', JSON.stringify(errorContext, null, 2));
        next(err);
      });
  });

  return server;
}

export function run(): void {
  // Parse and validate PORT environment variable
  const portEnv = process.env['PORT'];
  let port: number = 4000; // Default fallback

  if (portEnv !== undefined) {
    // Validate that the string contains only digits
    if (!/^\d+$/.test(portEnv)) {
      console.error(`Invalid PORT environment variable: "${portEnv}" is not a valid port number. Using default port 4000.`);
    } else {
      const parsedPort = parseInt(portEnv, 10);
      // Check if the port is within the valid range
      if (parsedPort < 1 || parsedPort > 65535) {
        console.error(`Invalid PORT environment variable: ${parsedPort} is out of valid port range (1-65535). Using default port 4000.`);
      } else {
        port = parsedPort;
      }
    }
  }

  // Start up the Node server
  const server = app();
  server.listen(port, () => {
    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  run();
}
