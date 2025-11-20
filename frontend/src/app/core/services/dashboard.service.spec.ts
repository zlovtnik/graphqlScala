import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Apollo } from 'apollo-angular';
import { DashboardService, DashboardStats, HealthStatusEnum } from './dashboard.service';
import { of, throwError } from 'rxjs';

describe('DashboardService - WebSocket/Subscription Tests', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;
  let apolloMock: jasmine.SpyObj<Apollo>;

  beforeEach(() => {
    const apolloSpyObj = jasmine.createSpyObj('Apollo', ['subscribe']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        DashboardService,
        { provide: Apollo, useValue: apolloSpyObj }
      ]
    });

    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
    apolloMock = TestBed.inject(Apollo) as jasmine.SpyObj<Apollo>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('GraphQL Subscription Flow', () => {
    it('should attempt subscription first', (done) => {
      // Arrange
      const mockStats: DashboardStats = {
        totalUsers: 200,
        activeSessions: 20,
        totalAuditLogs: 1000,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 100,
        failedLoginAttempts: 5,
        totalLoginAttempts: 50,
        loginAttemptTrends: []
      };

      const subscriptionResponse = {
        data: {
          dashboardStats: mockStats
        }
      };

      apolloMock.subscribe.and.returnValue(of(subscriptionResponse));

      // Act
      service.getStats().subscribe(stats => {
        // Assert
        expect(stats).toEqual(mockStats);
        expect(apolloMock.subscribe).toHaveBeenCalled();
        done();
      });
    });

    it('should handle subscription errors by falling back to polling', fakeAsync(() => {
      // Arrange
      const mockStats: DashboardStats = {
        totalUsers: 50,
        activeSessions: 5,
        totalAuditLogs: 250,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 25,
        failedLoginAttempts: 1,
        totalLoginAttempts: 25,
        loginAttemptTrends: []
      };

      apolloMock.subscribe.and.returnValue(throwError(() => new Error('WebSocket connection failed')));

      // Act
      service.getStats().subscribe(stats => {
        // Assert - should receive stats from polling fallback
        expect(stats).toBeDefined();
      });

      // Advance time to trigger the initial fetch
      tick(100);

      // Simulate HTTP response for polling
      const req = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    }));

    it('should process valid subscription response data', (done) => {
      // Arrange
      const mockStats: DashboardStats = {
        totalUsers: 150,
        activeSessions: 15,
        totalAuditLogs: 750,
        systemHealth: HealthStatusEnum.DEGRADED,
        loginAttemptsToday: 75,
        failedLoginAttempts: 5,
        totalLoginAttempts: 75,
        loginAttemptTrends: [
          { date: '2025-01-01', successCount: 50, failedCount: 2 },
          { date: '2025-01-02', successCount: 60, failedCount: 3 }
        ]
      };

      const subscriptionResponse = {
        data: {
          dashboardStats: mockStats
        }
      };

      apolloMock.subscribe.and.returnValue(of(subscriptionResponse));

      // Act
      service.getStats().subscribe(stats => {
        // Assert
        expect(stats.totalUsers).toBe(150);
        expect(stats.activeSessions).toBe(15);
        expect(stats.systemHealth).toBe('DEGRADED');
        expect(stats.loginAttemptTrends.length).toBe(2);
        done();
      });
    });

    it('should throw error on invalid subscription response', (done) => {
      // Arrange
      const invalidResponse = {
        data: undefined,
        errors: [{ message: 'Invalid query' }]
      };

      apolloMock.subscribe.and.returnValue(of(invalidResponse as any));

      // Act
      service.getStats().subscribe({
        next: () => {
          fail('Should not emit valid stats');
        },
        error: (error) => {
          // Assert
          expect(error).toBeDefined();
          done();
        }
      });
    });

    it('should handle subscription response missing dashboardStats field', (done) => {
      // Arrange
      const invalidResponse = {
        data: {
          someOtherField: {}
        }
      };

      apolloMock.subscribe.and.returnValue(of(invalidResponse));

      // Act
      service.getStats().subscribe({
        next: () => {
          fail('Should not emit when dashboardStats is missing');
        },
        error: (error) => {
          // Assert
          expect(error).toBeDefined();
          expect(error.message).toContain('Invalid subscription response');
          done();
        }
      });
    });
  });

  describe('Polling Fallback', () => {
    it('should poll every 5 seconds after subscription fails', fakeAsync(() => {
      // Arrange
      const mockStats: DashboardStats = {
        totalUsers: 30,
        activeSessions: 3,
        totalAuditLogs: 150,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 15,
        failedLoginAttempts: 0,
        totalLoginAttempts: 15,
        loginAttemptTrends: []
      };

      apolloMock.subscribe.and.returnValue(throwError(() => new Error('Connection failed')));

      // Act
      service.getStats().subscribe(stats => {
        // Assert
        expect(stats.totalUsers).toBe(30);
      });

      // Simulate first poll after initial subscription failure
      tick(100);
      const req1 = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      req1.flush(mockStats);

      // Verify polling continues after 5 seconds
      tick(5000);
      const req2 = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      req2.flush(mockStats);
    }));

    it('should return mock stats if polling fails', fakeAsync(() => {
      // Arrange
      apolloMock.subscribe.and.returnValue(throwError(() => new Error('Connection failed')));

      // Act
      service.getStats().subscribe(stats => {
        // Assert - should receive mock stats
        expect(stats).toBeDefined();
        expect(stats.systemHealth).toBe('HEALTHY');
      });

      // Simulate HTTP error on first poll
      tick(100);
      const req = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      req.error(new ErrorEvent('Network error'), { status: 500 });
    }));

    it('should handle polling errors gracefully', fakeAsync(() => {
      // Arrange
      apolloMock.subscribe.and.returnValue(throwError(() => new Error('WebSocket unavailable')));

      // Act
      service.getStats().subscribe(stats => {
        // Assert - should still emit even if polling fails
        expect(stats).toBeDefined();
      });

      // Simulate 404 error
      tick(100);
      const req = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      req.error(new ErrorEvent('Not found'), { status: 404 });
    }));
  });

  describe('Subscription Error Handling', () => {
    it('should handle subscription errors and warn in console', fakeAsync(() => {
      // Arrange
      spyOn(console, 'warn');
      apolloMock.subscribe.and.returnValue(throwError(() => new Error('GraphQL error')));

      // Act
      service.getStats().subscribe({
        next: () => { /* allow to continue to polling */ }
      });

      // Assert
      tick(50);
      expect(console.warn).toHaveBeenCalledWith(
        jasmine.stringMatching('Subscription failed'),
        jasmine.any(Error)
      );
    }));

    it('should emit mock stats on critical failures', fakeAsync(() => {
      // Arrange
      const mockStats = {
        totalUsers: 0,
        activeSessions: 1,
        totalAuditLogs: 0,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 0,
        failedLoginAttempts: 0,
        totalLoginAttempts: 0,
        loginAttemptTrends: []
      };

      apolloMock.subscribe.and.returnValue(throwError(() => new Error('Critical error')));

      // Act
      service.getStats().subscribe(stats => {
        // Assert
        expect(stats).toEqual(mockStats);
      });

      // Simulate all failures (subscription + polling)
      tick(100);
      const req = httpMock.expectOne(req => req.url.includes('/api/dashboard/stats'));
      req.error(new ErrorEvent('Network error'));
    }));
  });

  describe('Subscription Cleanup', () => {
    it('should unsubscribe and cleanup resources', (done) => {
      // Arrange
      const mockStats: DashboardStats = {
        totalUsers: 40,
        activeSessions: 4,
        totalAuditLogs: 200,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 20,
        failedLoginAttempts: 1,
        totalLoginAttempts: 20,
        loginAttemptTrends: []
      };

      const subscriptionResponse = {
        data: {
          dashboardStats: mockStats
        }
      };

      apolloMock.subscribe.and.returnValue(of(subscriptionResponse as any));

      // Act
      const subscription = service.getStats().subscribe();
      subscription.unsubscribe();

      // Assert
      expect(subscription.closed).toBe(true);
      done();
    });
  });
});
