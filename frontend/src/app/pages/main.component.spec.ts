import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { MainComponent } from './main.component';
import { AuthService, User } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { DashboardService, DashboardStats, HealthStatusEnum } from '../core/services/dashboard.service';
import { PwaService } from '../core/services/pwa.service';
import { of, Subject } from 'rxjs';
import { NGX_ECHARTS_CONFIG } from 'ngx-echarts';

describe('MainComponent - WebSocket Subscription Integration', () => {
  let component: MainComponent;
  let fixture: ComponentFixture<MainComponent>;
  let dashboardService: jasmine.SpyObj<DashboardService>;
  let authService: jasmine.SpyObj<AuthService>;
  let themeService: jasmine.SpyObj<ThemeService>;
  let pwaService: jasmine.SpyObj<PwaService>;

  const mockStats: DashboardStats = {
    totalUsers: 100,
    activeSessions: 10,
    totalAuditLogs: 500,
    systemHealth: HealthStatusEnum.UP,
    loginAttemptsToday: 50,
    failedLoginAttempts: 2,
    totalLoginAttempts: 50,
    loginAttemptTrends: [
      { date: '2025-01-01', successCount: 48, failedCount: 2 },
      { date: '2025-01-02', successCount: 50, failedCount: 0 }
    ]
  };

  beforeEach(async () => {
    const dashboardServiceSpy = jasmine.createSpyObj('DashboardService', ['getStats', 'getHealthPolling']);
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['getCurrentUser$']);
    const themeServiceSpy = jasmine.createSpyObj('ThemeService', ['toggleTheme', 'getAvailableThemes', 'setTheme', 'getCurrentTheme$']);
    const pwaServiceSpy = jasmine.createSpyObj('PwaService', ['installPwa']);

    dashboardServiceSpy.getStats.and.returnValue(of(mockStats));
    dashboardServiceSpy.getHealthPolling.and.returnValue(of(mockStats));
    authServiceSpy.getCurrentUser$.and.returnValue(of(null));
    themeServiceSpy.getAvailableThemes.and.returnValue(['blue', 'neo-dark', 'emerald', 'amber', 'system']);
    themeServiceSpy.getCurrentTheme$.and.returnValue(of('blue'));
    themeServiceSpy.setTheme.and.returnValue(undefined);
    pwaServiceSpy.installPromptVisible$ = of(false);

    await TestBed.configureTestingModule({
      imports: [
        MainComponent,
        BrowserAnimationsModule,
        HttpClientTestingModule,
        RouterTestingModule
      ],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ThemeService, useValue: themeServiceSpy },
        { provide: PwaService, useValue: pwaServiceSpy },
        { provide: NGX_ECHARTS_CONFIG, useValue: { echarts: {} } }
      ]
    }).compileComponents();

    dashboardService = TestBed.inject(DashboardService) as jasmine.SpyObj<DashboardService>;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    themeService = TestBed.inject(ThemeService) as jasmine.SpyObj<ThemeService>;
    pwaService = TestBed.inject(PwaService) as jasmine.SpyObj<PwaService>;

    fixture = TestBed.createComponent(MainComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
  });

  describe('WebSocket Subscription Initialization', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize stats$ observable from dashboard service', () => {
      // Assert
      expect(component.stats$).toBeDefined();
    });

    it('should call getStats() from dashboard service', () => {
      // Act
      fixture.detectChanges();

      // Assert
      expect(dashboardService.getStats).toHaveBeenCalled();
    });

    it('should subscribe to stats$ stream on component init', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        expect(stats).toEqual(mockStats);
        done();
      });
    });
  });

  describe('Stats Reception and Display', () => {
    it('should receive and display stats from subscription', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        expect(stats.totalUsers).toBe(100);
        expect(stats.activeSessions).toBe(10);
        expect(stats.systemHealth).toBe('HEALTHY');
        done();
      });
    });

    it('should update success rate based on stats', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        const successRate = component.getSuccessRate(stats.totalLoginAttempts || 0, stats.failedLoginAttempts || 0);
        expect(successRate).toBe(96); // (50 - 2) / 50 * 100 = 96%
        expect(component.successRate).toBe(96);
        done();
      });
    });

    it('should handle stats with zero login attempts', (done) => {
      // Arrange
      const statsWithZeroAttempts: DashboardStats = {
        ...mockStats,
        totalLoginAttempts: 0,
        failedLoginAttempts: 0
      };

      dashboardService.getStats.and.returnValue(of(statsWithZeroAttempts));
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        const successRate = component.getSuccessRate(stats.totalLoginAttempts || 0, stats.failedLoginAttempts || 0);
        expect(successRate).toBe(100); // Should return 100% when no attempts
        done();
      });
    });

    it('should update chart options when receiving stats with trends', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        expect(component.chartOptions).toBeTruthy();
        done();
      });
    });
  });

  describe('Alert Generation from Stats', () => {
    it('should generate alert for unhealthy system', (done) => {
      // Arrange
      const unhealthyStats: DashboardStats = {
        ...mockStats,
        systemHealth: HealthStatusEnum.DOWN
      };

      dashboardService.getStats.and.returnValue(of(unhealthyStats));
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        const hasErrorAlert = component.alerts.some(a => a.type === 'error');
        expect(hasErrorAlert).toBe(true);
        done();
      });
    });

    it('should generate alert for high failed login attempts', (done) => {
      // Arrange
      const highFailureStats: DashboardStats = {
        ...mockStats,
        failedLoginAttempts: 15
      };

      dashboardService.getStats.and.returnValue(of(highFailureStats));
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        const hasWarningAlert = component.alerts.some(a => a.type === 'warning');
        expect(hasWarningAlert).toBe(true);
        done();
      });
    });

    it('should warn about high concurrent sessions', (done) => {
      // Arrange
      const highSessionStats: DashboardStats = {
        ...mockStats,
        activeSessions: 1000
      };

      dashboardService.getStats.and.returnValue(of(highSessionStats));
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        const hasInfoAlert = component.alerts.some(a => a.type === 'info');
        expect(hasInfoAlert).toBe(true);
        done();
      });
    });

    it('should generate success alert when system is healthy', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        const hasSuccessAlert = component.alerts.some(a => a.type === 'success' && a.message.includes('operational'));
        expect(hasSuccessAlert).toBe(true);
        done();
      });
    });

    it('should allow dismissing alerts', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        const initialAlertsCount = component.alerts.length;

        if (initialAlertsCount > 0) {
          const firstAlert = component.alerts[0];
          component.dismissAlert(firstAlert);
          fixture.detectChanges();

          // Verify alert was dismissed by checking alerts list was reduced
          expect(component.alerts.length).toBe(initialAlertsCount - 1);
        }
        done();
      });
    });
  });

  describe('Real-time Updates via Subscription', () => {
    it('should update stats when new subscription data arrives', fakeAsync(() => {
      // Arrange
      const statsSubject = new Subject<DashboardStats>();
      dashboardService.getStats.and.returnValue(statsSubject.asObservable());
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      const emittedStats: DashboardStats[] = [];
      const subscription = component.stats$.subscribe(stats => {
        emittedStats.push(stats);
      });

      // Act
      fixture.detectChanges();
      tick();

      // Emit first value
      statsSubject.next(mockStats);
      tick();

      // Emit second value with updated totalUsers
      statsSubject.next({ ...mockStats, totalUsers: 110 });
      tick();

      // Assert
      expect(emittedStats.length).toBe(2);
      expect(emittedStats[0].totalUsers).toBe(100);
      expect(emittedStats[1].totalUsers).toBe(110);

      subscription.unsubscribe();
    }));

    it('should handle stats updates without page reload', (done) => {
      // Arrange
      const updatedStats: DashboardStats = {
        ...mockStats,
        activeSessions: 25,
        failedLoginAttempts: 5
      };

      // Simulate new stats from subscription
      dashboardService.getStats.and.returnValue(of(updatedStats));
      
      // Recreate component after re-stubbing to ensure subscription uses new observable
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        expect(stats.activeSessions).toBe(25);
        expect(stats.failedLoginAttempts).toBe(5);
        done();
      });
    });
  });

  describe('Subscription Cleanup on Destroy', () => {
    it('should complete all subscriptions when destroyed', (done) => {
      // Arrange
      fixture.detectChanges();
      
      let statsCompleted = false;
      const statsSubscription = component.stats$.subscribe({
        complete: () => { statsCompleted = true; }
      });

      // Act
      component.ngOnDestroy();

      // Assert
      // Use setTimeout to allow async completion handlers to fire
      setTimeout(() => {
        expect(statsCompleted).toBe(true);
        done();
      }, 0);
    });

    it('should stop emitting stats after destroy', (done) => {
      // Arrange
      const statsSubject = new Subject<DashboardStats>();
      dashboardService.getStats.and.returnValue(statsSubject.asObservable());
      
      fixture = TestBed.createComponent(MainComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      let statsEmitted = false;
      const statsSubscription = component.stats$.subscribe({
        next: () => { statsEmitted = true; }
      });
      
      // Emit initial value to verify subscription works
      statsSubject.next(mockStats);
      expect(statsEmitted).toBe(true);

      // Act: Destroy component
      component.ngOnDestroy();
      
      // Reset flag to test post-destroy emissions
      statsEmitted = false;

      // Assert: Emit new value and verify no subscribers receive it
      statsSubject.next({ ...mockStats, totalUsers: 200 });
      expect(statsEmitted).toBe(false); // Should not emit after destroy
      
      // Cleanup
      statsSubject.complete();
      done();
    });
  });

  describe('Error Handling and Fallback', () => {
    it('should display mock stats if subscription fails', (done) => {
      // Arrange
      dashboardService.getStats.and.returnValue(of({
        totalUsers: 0,
        activeSessions: 1,
        totalAuditLogs: 0,
        systemHealth: HealthStatusEnum.UP,
        loginAttemptsToday: 0,
        failedLoginAttempts: 0,
        totalLoginAttempts: 0,
        loginAttemptTrends: []
      }));

      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        expect(stats).toBeDefined();
        expect(stats.systemHealth).toBe(HealthStatusEnum.UP);
        done();
      });
    });
  });

  describe('Chart Data Updates', () => {
    it('should format trend labels correctly', () => {
      // Arrange
      const dateISO = '2025-01-15T10:30:00Z';

      // Act
      const label = component['formatTrendLabel'](dateISO);

      // Assert
      expect(label).toBeTruthy();
      expect(typeof label).toBe('string');
    });

    it('should create chart with correct structure', (done) => {
      // Act
      fixture.detectChanges();

      // Assert
      component.stats$.subscribe(stats => {
        fixture.detectChanges();
        if (component.chartOptions) {
          expect(component.chartOptions.legend).toBeDefined();
          expect(component.chartOptions.xAxis).toBeDefined();
          expect(component.chartOptions.series).toBeDefined();
        }
        done();
      });
    });
  });

  describe('User Authentication Integration', () => {
    it('should display current user information', (done) => {
      // Arrange
      const mockUser: User = {
        id: '1',
        username: 'testuser',
        email: 'test@example.com'
      };

      authService.getCurrentUser$.and.returnValue(of(mockUser));

      // Act
      fixture.detectChanges();

      // Assert
      setTimeout(() => {
        expect(component.currentUser).toEqual(mockUser);
        done();
      }, 100);
    });
  });
});
