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
import { takeUntil, shareReplay } from 'rxjs/operators';
import { AuthService, User } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { DashboardService, DashboardStats, LoginAttemptTrendPoint } from '../core/services/dashboard.service';
import { PwaService } from '../core/services/pwa.service';
import { NgxEchartsModule } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';

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
    NzAlertModule,
    NgxEchartsModule
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

  chartOptions: EChartsOption | null = null;
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
    // Use shareReplay(1) to ensure all subscribers (async pipes + explicit subscription)
    // share a single HTTP request instead of triggering multiple fetches
    this.stats$ = this.dashboardService.getStats().pipe(shareReplay(1));
  }

  ngOnInit(): void {
    // Subscribe to user changes with proper cleanup
    // Route access is controlled by authGuard
    this.authService.getCurrentUser$()
      .pipe(takeUntil(this.destroy$))
      .subscribe((user: User | null) => {
        this.currentUser = user;
      });

    // Initialize alerts based on stats
    this.stats$.pipe(takeUntil(this.destroy$)).subscribe(stats => {
      this.updateAlerts(stats);
      this.successRate = this.getSuccessRate(stats.totalLoginAttempts || 0, stats.failedLoginAttempts || 0);
      this.updateChartOptions(stats.loginAttemptTrends);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  async logout(): Promise<void> {
    try {
      await this.authService.logout();
    } catch (error) {
      console.warn('Logout failed:', error);
    }
    await this.router.navigate(['/login']);
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

  getSuccessRate(totalAttempts: number, failedAttempts: number): number {
    if (totalAttempts === 0) {
      return 100;
    }

    const successfulAttempts = Math.max(totalAttempts - failedAttempts, 0);
    return Math.round((successfulAttempts / totalAttempts) * 100);
  }

  installPwa(): void {
    this.pwaService.installPwa();
  }

  private updateChartOptions(trends?: LoginAttemptTrendPoint[]): void {
    if (!trends || trends.length === 0) {
      this.chartOptions = null;
      return;
    }

    const labels = trends.map(point => this.formatTrendLabel(point.date));
    const successCounts = trends.map(point => point.successCount);
    const failedCounts = trends.map(point => point.failedCount);

    this.chartOptions = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['Successful', 'Failed']
      },
      grid: {
        left: '3%',
        right: '3%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisTick: { alignWithLabel: true }
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      series: [
        {
          name: 'Successful',
          type: 'bar',
          stack: 'loginAttempts',
          data: successCounts,
          itemStyle: {
            color: '#52c41a'
          }
        },
        {
          name: 'Failed',
          type: 'bar',
          stack: 'loginAttempts',
          data: failedCounts,
          itemStyle: {
            color: '#ff4d4f'
          }
        }
      ]
    } satisfies EChartsOption;
  }

  private formatTrendLabel(dateIso: string): string {
    if (!dateIso) {
      return '';
    }

    const trimmed = dateIso.trim();
    const date = new Date(trimmed);

    // Validate the parsed date
    if (Number.isNaN(date.getTime())) {
      return dateIso;
    }

    return new Intl.DateTimeFormat(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric'
    }).format(date);
  }
}
