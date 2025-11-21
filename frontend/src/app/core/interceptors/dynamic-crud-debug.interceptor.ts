import { HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

/**
 * Debug interceptor that traces every Dynamic CRUD execute call so we can confirm
 * which URL Angular is hitting along with payload metadata.
 */
export const dynamicCrudDebugInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  if (req.url.includes('/api/dynamic-crud/execute')) {
    const absoluteUrl = req.url.startsWith('http') ? req.url : `${window.location.origin}${req.url}`;
    // eslint-disable-next-line no-console
    console.debug('[DynamicCrudDebug] Outgoing execute request', {
      method: req.method,
      relativeUrl: req.url,
      absoluteUrl,
      hasAuthorizationHeader: req.headers.has('Authorization'),
      contentType: req.headers.get('Content-Type'),
      bodyPreview: truncateBody(req.body)
    });

    const start = performance.now();
    return next(req).pipe(
      tap({
        next: () => {
          // eslint-disable-next-line no-console
          console.debug('[DynamicCrudDebug] Execute request succeeded', {
            durationMs: Math.round(performance.now() - start)
          });
        },
        error: (error) => {
          // eslint-disable-next-line no-console
          console.error('[DynamicCrudDebug] Execute request failed', {
            durationMs: Math.round(performance.now() - start),
            status: error?.status,
            statusText: error?.statusText,
            url: error?.url
          });
        }
      })
    );
  }

  return next(req);
};

function truncateBody(body: unknown): unknown {
  if (!body || typeof body !== 'object') {
    return body;
  }

  const json = JSON.stringify(body);
  if (json.length <= 500) {
    return body;
  }

  try {
    return JSON.parse(json.substring(0, 500));
  } catch (err) {
    return json.substring(0, 500);
  }
}
