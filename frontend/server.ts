import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine } from '@angular/ssr';
import express from 'express';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import bootstrap from './src/main.server';

// The Express app is exported so that it can be used by serverless Functions.
export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const indexHtml = join(serverDistFolder, 'index.server.html');

  const commonEngine = new CommonEngine();

  server.set('view engine', 'html');
  server.set('views', browserDistFolder);

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
  const hostPattern = /^[A-Za-z0-9.-]+(:\d{1,5})?$/;

  // All regular routes use the Angular engine
  server.get('**', (req, res, next) => {
    const { protocol, originalUrl, baseUrl, headers } = req;
    const hostHeader = String(headers.host ?? '').trim();

    if (!hostHeader || !hostPattern.test(hostHeader) ||
      (allowedHosts.length > 0 && !allowedHosts.includes(hostHeader))) {
      console.warn(`Rejected request with disallowed host header: "${hostHeader}"`);
      res.status(400).send('Invalid host header');
      return;
    }

    commonEngine
      .render({
        bootstrap,
        documentFilePath: indexHtml,
        url: `${protocol}://${hostHeader}${originalUrl}`,
        publicPath: browserDistFolder,
        providers: [{ provide: APP_BASE_HREF, useValue: baseUrl }],
      })
      .then((html) => res.send(html))
      .catch((err) => next(err));
  });

  return server;
}

export function run(): void {
  const port = process.env['PORT'] || 4000;

  // Start up the Node server
  const server = app();
  server.listen(port, () => {
    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  run();
}
