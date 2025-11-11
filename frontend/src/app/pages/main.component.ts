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

interface SystemAlert {
  type: 'success' | 'info' | 'warning' | 'error';
  message: string;
  timestamp: Date;
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
  private destroy$ = new Subject<void>();

  private authService = inject(AuthService);
  protected themeService = inject(ThemeService);
  private router = inject(Router);
  private dashboardService = inject(DashboardService);

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

    // Initialize alerts based on stats
    this.stats$.pipe(takeUntil(this.destroy$)).subscribe(stats => {
      this.updateAlerts(stats);
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

  private updateAlerts(stats: DashboardStats): void {
    this.alerts = [];

    if (stats.systemHealth !== 'HEALTHY') {
      this.alerts.push({
        type: 'error',
        message: 'System health is degraded. Please check system status.',
        timestamp: new Date()
      });
    }

    if (stats.failedLoginAttempts > 10) {
      this.alerts.push({
        type: 'warning',
        message: `High number of failed login attempts: ${stats.failedLoginAttempts}`,
        timestamp: new Date()
      });
    }

    if (stats.activeSessions > 50) {
      this.alerts.push({
        type: 'info',
        message: `High active sessions: ${stats.activeSessions} users online`,
        timestamp: new Date()
      });
    }

    // Always show a success alert if system is healthy
    if (stats.systemHealth === 'HEALTHY' && this.alerts.length === 0) {
      this.alerts.push({
        type: 'success',
        message: 'All systems operational',
        timestamp: new Date()
      });
    }
  }

  getRandomHeight(day: number): number {
    // Mock data for chart bars
    const heights = [65, 80, 45, 90, 70, 55, 85];
    return heights[day - 1] || 50;
  }

  getRandomValue(day: number): number {
    // Mock values for user activity
    const values = [12, 19, 8, 21, 15, 10, 18];
    return values[day - 1] || 0;
  }

  getSuccessRate(totalLogs: number, failedAttempts: number): number {
    if (totalLogs === 0) return 100;
    return Math.round(((totalLogs - failedAttempts) / totalLogs) * 100);
  }
}
