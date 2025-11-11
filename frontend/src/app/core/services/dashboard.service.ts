import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, delay } from 'rxjs/operators';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';

export interface DashboardStats {
  totalUsers: number;
  activeSessions: number;
  totalAuditLogs: number;
  systemHealth: string;
  apiCallsToday: number;
  failedLoginAttempts: number;
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly apiUrl = '/api/dashboard/stats';
  private http = inject(HttpClient);

  /**
   * Fetch dashboard statistics from the server
   * Falls back to mock data if the API call fails
   * @returns Observable of dashboard stats with error handling
   */
  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(this.apiUrl).pipe(
      // Add a small delay to avoid UI jank during initial load
      delay(300),
      catchError((error: HttpErrorResponse) => {
        console.warn('Failed to fetch dashboard stats, using fallback data:', error.message);
        // Return mock/fallback data with default values
        return of(this.getMockStats());
      })
    );
  }

  /**
   * Get mock statistics for development/fallback
   * These values are used when the API is unavailable
   */
  private getMockStats(): DashboardStats {
    return {
      totalUsers: 0,
      activeSessions: 1,
      totalAuditLogs: 0,
      systemHealth: 'HEALTHY',
      apiCallsToday: 0,
      failedLoginAttempts: 0
    };
  }
}
