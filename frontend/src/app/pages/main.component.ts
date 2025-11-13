import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzStatisticModule } from 'ng-zorro-antd/statistic';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { Subject, Observable } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService, User } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { DashboardService, DashboardStats } from '../core/services/dashboard.service';
import { PwaService } from '../core/services/pwa.service';

interface SystemAlert {
  type: 'success' | 'info' | 'warning' | 'error';
  message: string;
  timestamp: Date;
  key: string;
}

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [
    CommonModule,
    NzCardModule,
    NzButtonModule,
    NzGridModule,
    NzIconModule,
    NzStatisticModule,
    NzAvatarModule,
    NzTagModule,
    NzAlertModule
  ],
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  stats$: Observable<DashboardStats>;
  alerts: SystemAlert[] = [];
  private dismissedAlerts: Set<string> = new Set();
  private destroy$ = new Subject<void>();

  chartData: { day: string; height: number; value: number }[] = [];
  successRate: number = 0;

  private authService = inject(AuthService);
  protected themeService = inject(ThemeService);
  private router = inject(Router);
  private dashboardService = inject(DashboardService);
  private pwaService = inject(PwaService);

  get installPromptVisible$() {
    return this.pwaService.installPromptVisible$;
  }

  constructor() {
    // Initialize stats$ from dashboard service with error handling
    this.stats$ = this.dashboardService.getStats();
  }

  ngOnInit(): void {
    // Subscribe to user changes with proper cleanup
    // Route access is controlled by authGuard
    this.authService.getCurrentUser$()
      .pipe(takeUntil(this.destroy$))
      .subscribe((user: User | null) => {
        this.currentUser = user;
      });

    // Initialize chartData with mock data
    this.chartData = [
      { day: 'Mon', height: 65, value: 12 },
      { day: 'Tue', height: 80, value: 19 },
      { day: 'Wed', height: 45, value: 8 },
      { day: 'Thu', height: 90, value: 21 },
      { day: 'Fri', height: 70, value: 15 },
      { day: 'Sat', height: 55, value: 10 },
      { day: 'Sun', height: 85, value: 18 }
    ];

    // Initialize alerts based on stats
    this.stats$.pipe(takeUntil(this.destroy$)).subscribe(stats => {
      this.updateAlerts(stats);
      this.successRate = this.getSuccessRate(stats.totalAuditLogs || 0, stats.failedLoginAttempts || 0);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  navigateToUsers(): void {
    this.router.navigate(['/users']);
  }

  navigateToDynamicCrud(): void {
    this.router.navigate(['/dynamic-crud']);
  }

  navigateToSettings(): void {
    this.router.navigate(['/settings']);
  }

  dismissAlert(alert: SystemAlert): void {
    this.dismissedAlerts.add(alert.key);
    this.alerts = this.alerts.filter(a => a.key !== alert.key);
  }

  private updateAlerts(stats: DashboardStats): void {
    const nextAlerts: SystemAlert[] = [];

    if (stats.systemHealth !== 'HEALTHY') {
      const key = 'system-health-error';
      if (!this.dismissedAlerts.has(key)) {
        nextAlerts.push({
          type: 'error',
          message: 'System health is degraded. Please check system status.',
          timestamp: new Date(),
          key
        });
      }
    }

    if (stats.failedLoginAttempts > 10) {
      const key = 'failed-attempts-warning';
      if (!this.dismissedAlerts.has(key)) {
        nextAlerts.push({
          type: 'warning',
          message: `High number of failed login attempts: ${stats.failedLoginAttempts}`,
          timestamp: new Date(),
          key
        });
      }
    }

    if (stats.activeSessions > 50) {
      const key = 'active-sessions-info';
      if (!this.dismissedAlerts.has(key)) {
        nextAlerts.push({
          type: 'info',
          message: `High active sessions: ${stats.activeSessions} users online`,
          timestamp: new Date(),
          key
        });
      }
    }

    // Always show a success alert if system is healthy
    if (stats.systemHealth === 'HEALTHY' && nextAlerts.length === 0) {
      const key = 'system-health-success';
      if (!this.dismissedAlerts.has(key)) {
        nextAlerts.push({
          type: 'success',
          message: 'All systems operational',
          timestamp: new Date(),
          key
        });
      }
    }

    this.alerts = nextAlerts;
  }

  getSuccessRate(totalLogs: number, failedAttempts: number): number {
    if (totalLogs === 0) return 100;
    return Math.round(((totalLogs - failedAttempts) / totalLogs) * 100);
  }

  installPwa(): void {
    this.pwaService.installPwa();
  }
}
