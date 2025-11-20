import { Injectable, inject } from '@angular/core';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Apollo, gql } from 'apollo-angular';
import { environment } from '../../../environments/environment';
import { DASHBOARD_STATS_SUBSCRIPTION } from '../../graphql/subscriptions/dashboardStats';

/**
 * Health status enum matching GraphQL HealthStatus enum
 */
export enum HealthStatusEnum {
  UP = 'UP',
  DOWN = 'DOWN',
  DEGRADED = 'DEGRADED'
}

export interface LoginAttemptTrendPoint {
  date: string; // ISO 8601 date string (yyyy-MM-dd) from Date scalar
  successCount: number;
  failedCount: number;
}

export interface HealthDependency {
  name: string;
  status: string; // UP, DOWN, DEGRADED, UNKNOWN
  detail: string;
  lastChecked: string;
  responseTimeMs: number;
}

export interface HealthAlert {
  severity: string; // INFO, WARNING, ERROR, CRITICAL
  message: string;
  component: string;
  suggestedAction: string;
  timestamp: string;
}

export interface CircuitBreakerStatus {
  states: Record<string, string>; // name -> state (CLOSED, OPEN, HALF_OPEN)
  failureRates: Record<string, number>; // name -> failure rate percentage
  slowCallRates: Record<string, number>; // name -> slow call rate percentage
}

export interface SystemHealth {
  status: string; // UP, DOWN, DEGRADED
  dependencies: HealthDependency[];
  alerts: HealthAlert[];
  circuitBreakerStatus: CircuitBreakerStatus;
  timestamp: string;
  uptimeSeconds: number;
}

export interface DashboardStats {
  totalUsers: number;
  activeSessions: number;
  totalAuditLogs: number;
  systemHealth: HealthStatusEnum;
  loginAttemptsToday: number;
  failedLoginAttempts: number;
  totalLoginAttempts: number;
  loginAttemptTrends: LoginAttemptTrendPoint[];
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly statsEndpoint = `${environment.apiUrl}/api/dashboard/stats`;
  private readonly healthEndpoint = `${environment.apiUrl}/api/dashboard/health`;
  private http = inject(HttpClient);
  private apollo = inject(Apollo);

  /**
   * Subscribe to dashboard statistics updates
   * Falls back to polling if subscription fails
   * @returns Observable of dashboard stats
   */
  getStats(): Observable<DashboardStats> {
    return this.apollo.subscribe({
      query: DASHBOARD_STATS_SUBSCRIPTION
    }).pipe(
      map((result: any) => {
        if (result.errors && result.errors.length > 0) {
          throw new Error(result.errors[0].message || 'GraphQL error');
        }
        if (!result.data || !result.data.dashboardStats) {
          throw new Error('Invalid subscription response');
        }
        return result.data.dashboardStats;
      }),
      catchError((error) => {
        console.warn('Subscription failed, falling back to polling:', error);
        // Fallback to polling every 5 seconds
        return this.pollStats();
      })
    );
  }

  /**
   * Get system health including dependency statuses and alerts
   * @returns Observable of system health data
   */
  getHealth(): Observable<SystemHealth> {
    return this.http.get<SystemHealth>(this.healthEndpoint).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Failed to fetch system health:', error.message);
        return of(this.getMockHealth());
      })
    );
  }

  /**
   * Poll system health every 5 seconds
   */
  getHealthPolling(): Observable<SystemHealth> {
    return new Observable<SystemHealth>((observer) => {
      let subscription: Subscription | undefined;
      const fetchHealth = () => {
        subscription = this.http.get<SystemHealth>(this.healthEndpoint).pipe(
          catchError((error: HttpErrorResponse) => {
            console.error('Failed to fetch system health:', error.message);
            return of(this.getMockHealth());
          })
        ).subscribe(health => {
          observer.next(health);
        });
      };

      fetchHealth(); // Initial fetch
      const interval = setInterval(fetchHealth, 5000); // Poll every 5 seconds

      return () => {
        clearInterval(interval);
        if (subscription) {
          subscription.unsubscribe();
        }
      };
    });
  }

  /**
   * Poll dashboard statistics every 5 seconds
   */
  private pollStats(): Observable<DashboardStats> {
    return new Observable<DashboardStats>((observer) => {
      let httpSubscription: Subscription | undefined;
      
      const fetchStats = () => {
        // Unsubscribe from previous request to avoid overlap
        if (httpSubscription) {
          httpSubscription.unsubscribe();
        }
        
        // Create new subscription for the HTTP request
        httpSubscription = this.http.get<DashboardStats>(this.statsEndpoint).pipe(
          catchError((error: HttpErrorResponse) => {
            console.error('Failed to fetch dashboard stats:', error.message);
            return of(this.getMockStats());
          })
        ).subscribe(stats => {
          observer.next(stats);
        });
      };

      fetchStats(); // Initial fetch
      const interval = setInterval(fetchStats, 5000); // Poll every 5 seconds

      return () => {
        clearInterval(interval);
        if (httpSubscription) {
          httpSubscription.unsubscribe();
        }
      };
    });
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
      systemHealth: HealthStatusEnum.UP,
      loginAttemptsToday: 0,
      failedLoginAttempts: 0,
      totalLoginAttempts: 0,
      loginAttemptTrends: []
    };
  }

  /**
   * Get mock system health for development/fallback
   */
  private getMockHealth(): SystemHealth {
    return {
      status: 'UP',
      dependencies: [
        {
          name: 'database',
          status: 'UP',
          detail: 'Database connection is available',
          lastChecked: new Date().toISOString(),
          responseTimeMs: 0
        },
        {
          name: 'redis',
          status: 'UP',
          detail: 'Redis connection is available',
          lastChecked: new Date().toISOString(),
          responseTimeMs: 0
        },
        {
          name: 'minio',
          status: 'UP',
          detail: 'MinIO is reachable',
          lastChecked: new Date().toISOString(),
          responseTimeMs: 0
        }
      ],
      alerts: [],
      circuitBreakerStatus: {
        states: {
          'database': 'CLOSED',
          'redis': 'CLOSED',
          'minio': 'CLOSED',
          'auth-service': 'CLOSED',
          'audit-service': 'CLOSED'
        },
        failureRates: {
          'database': 0,
          'redis': 0,
          'minio': 0,
          'auth-service': 0,
          'audit-service': 0
        },
        slowCallRates: {
          'database': 0,
          'redis': 0,
          'minio': 0,
          'auth-service': 0,
          'audit-service': 0
        }
      },
      timestamp: new Date().toISOString(),
      uptimeSeconds: 0
    };
  }
}
