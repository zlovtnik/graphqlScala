import { inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandlerFn,
  HttpEvent,
  HttpInterceptorFn,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { TokenStorageAdapter } from '../services/token-storage.adapter';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

/**
 * HTTP Interceptor for JWT authentication
 * Automatically adds the JWT token to all outgoing HTTP requests
 * Handles 401 responses by redirecting to login
 * 
 * Token refresh is handled proactively by RefreshTokenService,
 * so 401s indicate a genuine authentication failure (not expiration).
 */
export const jwtInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> => {
  const tokenStorage = inject(TokenStorageAdapter);
  const authService = inject(AuthService);
  const router = inject(Router);

  // Get the auth token from storage
  const token = tokenStorage.getToken();

  // If we have a token, add it to the request headers
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 Unauthorized responses
      if (error.status === 401) {
        // Token refresh failed or missing - logout and redirect to login
        authService.logout().then(() => {
          router.navigate(['/auth/login']);
        });
      }

      return throwError(() => error);
    })
  );
};
